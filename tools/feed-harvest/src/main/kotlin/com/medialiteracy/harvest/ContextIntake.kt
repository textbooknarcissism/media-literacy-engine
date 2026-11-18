package com.medialiteracy.harvest

import com.medialiteracy.feeds.ArticleTypeClassifier
import com.medialiteracy.feeds.FeedParser
import com.medialiteracy.feeds.PageMetadata
import com.medialiteracy.flagging.SummaryContextResolver
import java.io.File
import com.medialiteracy.model.FeedItem
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One headline for the context-labeled set (context plan, Phase 3).
 *
 * Carries what a reader needs to judge the headline as published: the link,
 * the feed summary and what the page says it is. It holds NO detector output,
 * so labeling stays blind to the engine.
 */
internal data class ContextRow(
    val id: String,
    val beat: String,
    val outlet: String,
    val articleType: String,
    val typeSource: String,
    val feedUrl: String,
    val link: String,
    val harvestedAt: String,
    val headline: String,
    val summary: String,
    val pageSection: String = "",
    val pageType: String = "",
    val fetchStatus: String = "unknown",
) {
    fun toTsv(): String = listOf(
        id, beat, outlet, articleType, typeSource, feedUrl, link, harvestedAt,
        headline, summary, pageSection, pageType, fetchStatus,
    ).joinToString("\t") { tsvClean(it) }

    companion object {
        val COLUMNS = listOf(
            "id", "beat", "outlet", "articleType", "typeSource", "feedUrl", "link", "harvestedAt",
            "headline", "summary", "pageSection", "pageType", "fetchStatus",
        )

        fun parse(line: String): ContextRow? {
            val p = line.split('\t')
            if (p.size < COLUMNS.size) return null
            return ContextRow(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], p[12])
        }

        fun readAll(file: File): List<ContextRow> =
            if (!file.exists()) emptyList()
            else file.readLines(Charsets.UTF_8)
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("id\t") }
                .mapNotNull(::parse)
    }
}

/**
 * Whether [item] was published on or after [since]. With no cut-off every item
 * passes; with one, an undated item fails, because it may be older than the
 * cut-off and the point of one is headlines the rules have not seen.
 */
internal fun publishedSince(item: FeedItem, since: Instant?): Boolean =
    since == null || item.publishedAt?.let { !it.isBefore(since) } == true

/** Parses `--since=YYYY-MM-DD` (midnight UTC); null when the flag is absent. */
internal fun parseSince(args: Array<String>): Instant? =
    args.firstOrNull { it.startsWith("--since=") }?.substringAfter("--since=")
        ?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant() }

private fun tsvClean(text: String): String = text.replace(Regex("""[\t\r\n]+"""), " ").trim()

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal object ContextIntake {
    /** About 400 rows over the 64-feed panel. */
    const val DEFAULT_PER_OUTLET = 6
    const val SUMMARY_CHARS = 600

    private const val HEADER = "# Context-labelled set, intake. No detector labels: read each summary and story, then label blind.\n"

    /**
     * Picks up to [perOutlet] rows for each outlet, counting rows [existing] already holds.
     *
     * Within an outlet rows are ordered by a hash of the headline and the first
     * ones taken, so the same feeds give the same draw (the beats-2026 method).
     * Equal quotas per outlet balance the lean tags within a beat, because the
     * panel is built in left/right pairs. A link already in [existing], or in
     * [alreadyLabeled] (another set's rows), is skipped: a story still in its
     * feed a week later is not a fresh headline.
     */
    fun select(
        candidates: List<ContextRow>,
        existing: List<ContextRow>,
        perOutlet: Int,
        alreadyLabelled: Set<String> = emptySet(),
    ): List<ContextRow> {
        val seen = (existing.map { it.id } + alreadyLabelled).toMutableSet()
        val held = existing.groupingBy { it.outlet }.eachCount()
        return candidates
            .filter { it.id !in seen }
            .groupBy { it.outlet }
            .toSortedMap()
            .flatMap { (outlet, rows) ->
                rows.distinctBy { it.id }
                    .sortedBy { sha256(it.headline) }
                    .take((perOutlet - (held[outlet] ?: 0)).coerceAtLeast(0))
            }
    }

    /**
     * Reads the page for one selected row: from the cache if present, else
     * through [fetchPage]. A blocked outlet is marked `blocked` and never fetched;
     * read it by hand through the browser connector.
     */
    fun enrich(
        row: ContextRow,
        cacheDir: File,
        isBlocked: (String) -> Boolean,
        fetchPage: (String) -> String?,
    ): ContextRow {
        val cacheFile = File(cacheDir, "${row.id}.html")
        val html: String? = when {
            cacheFile.exists() -> runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
            isBlocked(row.link) -> return row.copy(fetchStatus = "blocked")
            else -> fetchPage(row.link)?.also { page ->
                runCatching { cacheDir.mkdirs(); cacheFile.writeText(page, Charsets.UTF_8) }
            }
        }
        if (html.isNullOrBlank()) return row.copy(fetchStatus = "failed")
        val meta = PageMetadata.extract(html, row.link)
        return row.copy(
            pageSection = meta.section.orEmpty(),
            pageType = meta.pageType.orEmpty(),
            fetchStatus = "ok",
            // The feed said nothing about type: let the page say it, and record that it did.
            articleType = row.articleType.ifEmpty { meta.articleType?.id.orEmpty() },
            typeSource = if (row.articleType.isEmpty() && meta.articleType != null) "page" else row.typeSource,
        )
    }

    /** Reads every panel feed and returns the candidate rows (pages not yet fetched). */
    fun candidates(
        panel: List<Source>,
        fetchFeed: (String) -> String,
        today: String,
        since: Instant? = null,
        log: (String) -> Unit = ::println,
    ): List<ContextRow> = panel.flatMap { source ->
        val xml = runCatching { fetchFeed(source.url) }
            .onFailure { log("  ${source.name.padEnd(24)} FAILED  ${it.message}") }
            .getOrNull() ?: return@flatMap emptyList()
        // A subreddit post stands for the article it links to: that address, and that outlet.
        val articles = if (RedditLinks.isSubreddit(source.url)) RedditLinks.articleByThread(xml) else null
        val all = runCatching { FeedParser.parse(xml).items }
            .onFailure { log("  ${source.name.padEnd(24)} FAILED  ${it.message}") }
            .getOrNull()
            ?.let { items -> if (articles == null) items else items.mapNotNull { item -> articles[item.link?.trim()]?.let { item.copy(link = it, summary = null) } } }
            ?: return@flatMap emptyList()
        val items = all.filter { publishedSince(it, since) }
        val dropped = if (items.size < all.size) "  (${all.size - items.size} before the cut-off or undated)" else ""
        log("  ${source.name.padEnd(24)} ${items.size} items$dropped")
        items.mapNotNull { item ->
            val link = item.link?.trim().orEmpty()
            val title = tsvClean(item.title)
            if (link.isEmpty() || title.isEmpty()) return@mapNotNull null
            val type = ArticleTypeClassifier.classify(link, item.categories)
            ContextRow(
                id = sha256(link),
                beat = source.kind,
                outlet = if (articles == null) source.name else RedditLinks.host(link),
                articleType = type?.id.orEmpty(),
                typeSource = if (type != null) "feed" else "",
                feedUrl = source.url,
                link = link,
                harvestedAt = today,
                headline = title,
                summary = tsvClean(SummaryContextResolver.sanitize(item.summary.orEmpty())).take(SUMMARY_CHARS),
            )
        }
    }

    /**
     * Ids already used by any other headline file next to [out]: the link
     * hashes in the context-format sets and intakes. Any 64-hex cell in a
     * row's first three columns counts.
     */
    fun labelledIds(out: File): Set<String> {
        val dir = out.absoluteFile.parentFile ?: return emptySet()
        val hex = Regex("^[0-9a-f]{64}$")
        return dir.listFiles { f -> f.extension == "tsv" && f.absolutePath != out.absolutePath }.orEmpty()
            .flatMap { f ->
                f.readLines(Charsets.UTF_8).asSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .flatMap { line -> line.split('\t').take(3).filter { hex.matches(it) } }
                    .toList()
            }.toSet()
    }

    fun run(
        out: File,
        panel: List<Source>,
        perOutlet: Int,
        cacheDir: File,
        fetchFeed: (String) -> String,
        fetchPage: (String) -> String?,
        isBlocked: (String) -> Boolean,
        today: String = LocalDate.now().toString(),
        since: Instant? = null,
    ): Int {
        val existing = ContextRow.readAll(out)
        val picked = select(candidates(panel, fetchFeed, today, since), existing, perOutlet, labelledIds(out))
        if (picked.isEmpty()) {
            println("Nothing new to add; ${out.name} holds ${existing.size}.")
            return 0
        }
        println("Reading ${picked.size} article pages...")
        val enriched = picked.map { enrich(it, cacheDir, isBlocked, fetchPage) }
        if (!out.exists()) {
            out.parentFile?.mkdirs()
            out.writeText(HEADER + ContextRow.COLUMNS.joinToString("\t") + "\n", Charsets.UTF_8)
        }
        out.appendText(enriched.joinToString("") { it.toTsv() + "\n" }, Charsets.UTF_8)
        val byStatus = enriched.groupingBy { it.fetchStatus }.eachCount()
        println("Added ${enriched.size} rows; ${out.name} now holds ${existing.size + enriched.size}. Pages: $byStatus")
        return 0
    }
}
