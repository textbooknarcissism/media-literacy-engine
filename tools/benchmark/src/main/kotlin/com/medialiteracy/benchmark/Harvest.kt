package com.medialiteracy.benchmark

import com.medialiteracy.feeds.FeedParser
import com.medialiteracy.flagging.SummaryContextResolver
import java.io.File
import java.time.LocalDate

/** A feed whose origin tells us the article type. */
data class TypedFeed(val outlet: String, val articleType: String, val typeSource: String, val url: String)

data class IntakeRow(
    val id: String,
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
    fun toTsv(): String =
        listOf(id, outlet, articleType, typeSource, feedUrl, link, harvestedAt, headline, summary, pageSection, pageType, fetchStatus).joinToString("\t") { tsvSafe(it) }

    companion object {
        val COLUMNS = listOf("id", "outlet", "articleType", "typeSource", "feedUrl", "link", "harvestedAt", "headline", "summary", "pageSection", "pageType", "fetchStatus")

        fun parse(line: String): IntakeRow? {
            val p = line.split('\t')
            if (p.size < 9) return null
            return IntakeRow(
                id = p[0],
                outlet = p[1],
                articleType = p[2],
                typeSource = p[3],
                feedUrl = p[4],
                link = p[5],
                harvestedAt = p[6],
                headline = p[7],
                summary = p[8],
                pageSection = p.getOrElse(9) { "" },
                pageType = p.getOrElse(10) { "" },
                fetchStatus = p.getOrElse(11) { "unknown" },
            )
        }

        fun readAll(file: File): List<IntakeRow> =
            if (!file.exists()) emptyList()
            else file.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull(::parse)
    }
}

private const val GUARDIAN_TONE = "https://www.theguardian.com/tone"

/**
 * Verified on 2026-09-27. Specific Guardian tones come before comment and
 * news, because an item listed in two feeds keeps the type of the first.
 * Excluded: The Telegraph (no working feed) and Independent Voices (1 item).
 */
val TYPED_FEEDS = listOf(
    TypedFeed("The Guardian", "editorial", "publisher-tag", "$GUARDIAN_TONE/editorials/rss"),
    TypedFeed("The Guardian", "letters", "publisher-tag", "$GUARDIAN_TONE/letters/rss"),
    TypedFeed("The Guardian", "review", "publisher-tag", "$GUARDIAN_TONE/reviews/rss"),
    TypedFeed("The Guardian", "analysis", "publisher-tag", "$GUARDIAN_TONE/analysis/rss"),
    TypedFeed("The Guardian", "feature", "publisher-tag", "$GUARDIAN_TONE/features/rss"),
    TypedFeed("The Guardian", "opinion", "publisher-tag", "$GUARDIAN_TONE/comment/rss"),
    TypedFeed("The Guardian", "news", "publisher-tag", "$GUARDIAN_TONE/news/rss"),
    TypedFeed("The New York Times", "opinion", "section-feed", "https://rss.nytimes.com/services/xml/rss/nyt/Opinion.xml"),
    TypedFeed("The New York Times", "news", "section-feed", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml"),
    TypedFeed("The Washington Post", "opinion", "section-feed", "https://feeds.washingtonpost.com/rss/opinions"),
    TypedFeed("The Washington Post", "news", "section-feed", "https://feeds.washingtonpost.com/rss/world"),
    TypedFeed("BBC News", "news", "section-feed", "https://feeds.bbci.co.uk/news/rss.xml"),
    TypedFeed("The Independent", "news", "section-feed", "https://www.independent.co.uk/news/uk/rss"),
    TypedFeed("Daily Mail", "opinion", "section-feed", "https://www.dailymail.co.uk/debate/index.rss"),
    TypedFeed("Daily Mail", "news", "section-feed", "https://www.dailymail.co.uk/news/index.rss"),
    TypedFeed("Daily Mirror", "news", "section-feed", "https://www.mirror.co.uk/news/?service=rss"),
)

object Harvest {
    /** 25% of the 600-headline target, so no outlet dominates the set. */
    const val OUTLET_CAP = 150
    const val OUTLET_TYPE_CAP = 75

    /** New rows to keep: unseen links only, within the caps, counting what [existing] already holds. */
    fun select(candidates: List<IntakeRow>, existing: List<IntakeRow>): List<IntakeRow> {
        val seen = existing.map { it.id }.toMutableSet()
        val perOutlet = existing.groupingBy { it.outlet }.eachCount().toMutableMap()
        val perType = existing.groupingBy { it.outlet to it.articleType }.eachCount().toMutableMap()
        val kept = mutableListOf<IntakeRow>()
        for (row in candidates) {
            if (!seen.add(row.id)) continue
            if ((perOutlet[row.outlet] ?: 0) >= OUTLET_CAP) continue
            if ((perType[row.outlet to row.articleType] ?: 0) >= OUTLET_TYPE_CAP) continue
            perOutlet.merge(row.outlet, 1, Int::plus)
            perType.merge(row.outlet to row.articleType, 1, Int::plus)
            kept += row
        }
        return kept
    }

    fun run(intake: File, cacheDir: File = ArticleCache.CACHE_DIR, fetch: (String) -> String = Http::text): Int {
        val today = LocalDate.now().toString()
        val candidates = mutableListOf<IntakeRow>()
        for (feed in TYPED_FEEDS) {
            val items = try {
                FeedParser.parse(fetch(feed.url)).items
            } catch (e: Exception) {
                println("FAILED ${feed.url}: ${e.message}")
                continue
            }
            println("${items.size.toString().padStart(4)}  ${feed.outlet} / ${feed.articleType}  ${feed.url}")
            for (item in items) {
                val link = item.link?.trim().orEmpty()
                val title = item.title.trim()
                if (link.isEmpty() || title.isEmpty()) continue
                candidates += IntakeRow(
                    id = sha256Hex(link),
                    outlet = feed.outlet,
                    articleType = feed.articleType,
                    typeSource = feed.typeSource,
                    feedUrl = feed.url,
                    link = link,
                    harvestedAt = today,
                    headline = tsvSafe(title),
                    summary = tsvSafe(SummaryContextResolver.sanitize(item.summary.orEmpty())).take(600),
                )
            }
        }
        val existing = IntakeRow.readAll(intake)
        val added = select(candidates, existing)
        val enriched = added.map { row ->
            if (row.link.isNotBlank()) {
                val cached = ArticleCache.fetchAndExtract(row.id, row.link, cacheDir = cacheDir, fetch = fetch)
                row.copy(
                    pageSection = cached.section,
                    pageType = cached.pageType,
                    fetchStatus = cached.fetchStatus,
                )
            } else {
                row
            }
        }
        if (!intake.exists()) {
            intake.parentFile?.mkdirs()
            intake.writeText(
                "# Unlabelled broadsheet intake. Written by ./gradlew :tools:benchmark:run --args=\"harvest\".\n" +
                    "# Article type comes from the feed each item was found in.\n" +
                    "# ${IntakeRow.COLUMNS.joinToString("\t")}\n",
                Charsets.UTF_8,
            )
        }
        intake.appendText(enriched.joinToString("") { it.toTsv() + "\n" }, Charsets.UTF_8)
        println("Added ${enriched.size} new items; intake now holds ${existing.size + enriched.size}.")
        return 0
    }
}
