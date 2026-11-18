package com.medialiteracy.harvest

import com.medialiteracy.feeds.ArticleText
import com.medialiteracy.feeds.ArticleTypeClassifier
import com.medialiteracy.feeds.FeedParser
import com.medialiteracy.feeds.PageMetadata
import com.medialiteracy.feeds.StoryFramingComparator
import com.medialiteracy.feeds.StoryGrouper
import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.flagging.SummaryContextResolver
import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.Category
import com.medialiteracy.model.FeedItem
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Pulls real headlines from a spread of news sites and runs the detector over
 * them.
 *
 * Run it with:  ./gradlew :tools:feed-harvest:run
 *
 * This exists because the handwritten test headlines cannot tell you whether
 * the rules work: they were written to match the rules. Only real headlines,
 * from sites nobody picked to flatter the detector, can do that.
 *
 * It adds to fixtures/headlines/harvested.tsv, with the detector's own guess as
 * the label for anything new. Those guesses are a starting point for a human to
 * correct, never a substitute for it. Scoring the rules against their own output
 * would be meaningless.
 *
 * Meant to be run repeatedly (daily for a week or two) because one day of news
 * is one day of news. See [merge]: previous runs and any labeling already done
 * are preserved.
 */

/**
 * How many headlines to take from any one source per run.
 *
 * Without this, the corpus is decided by whoever publishes the longest feed
 * rather than by anything anyone chose. In the first harvest the Daily Mail
 * handed over 144 headlines and Sky News handed over 9, so 71% of the 482 came
 * from three outlets and the rules were in effect being tuned on two newspapers.
 *
 * A cap plus repeated runs fixes that from both ends: the big feeds stop
 * drowning everyone, and the small feeds accumulate across days.
 */
private const val PER_SOURCE_CAP = 40

/**
 * A deliberately mixed panel: plain wire copy through to the noisiest tabloids.
 *
 * **Why the [Source.kind] tags name political lean.** They are here so the one
 * bias check that matters is possible at all: if the word lists have quietly
 * learned one side's vocabulary, the only way to see it is to compare the
 * flagged rate of outlets that lean opposite ways. The panel is therefore built
 * in **pairs**: for every right-leaning outlet, a comparable left-leaning one.
 * A lopsided panel produces a lopsided detector and no way to notice.
 *
 * These tags exist in this developer tool only. Nothing here reaches the app,
 * and nothing in the app knows an outlet's politics: that is decision 6, and
 * this file is not an exception to it. The panel is a test set, not a
 * recommendation: it deliberately contains outlets that would never ship as
 * defaults, because the rules need to see the full range of how a headline can
 * be written.
 */
internal val PANEL = listOf(
    // Baseline. Should be close to silent. If these light up, the rules are wrong.
    Source("UPI", "wire", "https://rss.upi.com/news/top_news.rss"),

    // Public broadcasters, several countries, to keep the panel from being all-UK.
    Source("PBS NewsHour", "public", "https://www.pbs.org/newshour/feeds/rss/headlines"),
    Source("NPR", "public", "https://feeds.npr.org/1001/rss.xml"),
    Source("Al Jazeera", "public", "https://www.aljazeera.com/xml/rss/all.xml"),
    Source("Deutsche Welle", "public", "https://rss.dw.com/rdf/rss-en-all"),

    // UK broadsheet, paired left and right.
    Source("BBC News", "uk-mainstream", "https://feeds.bbci.co.uk/news/rss.xml"),
    Source("Sky News", "uk-mainstream", "https://feeds.skynews.com/feeds/rss/home.xml"),
    Source("The Independent", "uk-mainstream", "https://www.independent.co.uk/news/uk/rss"),
    Source("The Guardian", "uk-left", "https://www.theguardian.com/uk/rss"),

    // UK tabloid, paired. The first harvest had two right-leaning tabloids and
    // no left-leaning one at all, which is exactly the skew the pairs are for.
    Source("Daily Mail", "uk-right", "https://www.dailymail.co.uk/articles.rss"),
    Source("Daily Express", "uk-right", "https://www.express.co.uk/posts/rss/1/news"),
    Source("Daily Mirror", "uk-left", "https://www.mirror.co.uk/news/?service=rss"),
    Source("Daily Star", "uk-left", "https://www.dailystar.co.uk/?service=rss"),

    // UK political opinion magazines, paired.
    Source("The Critic", "uk-right", "https://thecritic.co.uk/feed/"),
    Source("New Statesman", "uk-left", "https://www.newstatesman.com/feed"),

    // US digital politics & mainstream news
    Source("NBC News", "us-mainstream", "https://feeds.nbcnews.com/nbcnews/public/news"),
    Source("CBS News", "us-mainstream", "https://www.cbsnews.com/latest/rss/main"),
    Source("ABC News", "us-mainstream", "https://abcnews.go.com/abcnews/topstories"),
    Source("CNN", "us-mainstream", "http://rss.cnn.com/rss/cnn_topstories.rss"),
    Source("Fox News", "us-right", "https://moxie.foxnews.com/google-publisher/latest.xml"),
    Source("Politico", "us-mainstream", "https://rss.politico.com/politics-news.xml"),
    Source("The Hill", "us-mainstream", "https://thehill.com/feed/"),
    Source("Axios", "us-mainstream", "https://api.axios.com/feed/"),

    // Explicitly partisan online, paired. The sharpest test of whether the word
    // lists are even-handed, because the technique density is high on both.
    Source("Breitbart", "us-right", "https://feeds.feedburner.com/breitbart"),
    Source("Mother Jones", "us-left", "https://www.motherjones.com/feed/"),

    // Entertainment. Included because manufactured conflict is most concentrated
    // and most legible here, which is what makes it the best place to learn the
    // pattern, so it can be caught in politics and sport where it matters more.
    // Included in the test set does not mean shipped as a default feed.
    Source("E! Online", "entertainment", "https://www.eonline.com/syndication/feeds/rssfeeds/topstories.xml"),

    // Tech. Ars is deliberately careful; the others are where the second-person
    // hedge lives ("your robotaxi might be a narc").
    Source("Ars Technica", "tech-careful", "https://feeds.arstechnica.com/arstechnica/index"),
    Source("The Verge", "tech", "https://www.theverge.com/rss/index.xml"),
    Source("Wired", "tech", "https://www.wired.com/feed/rss"),
    Source("Engadget", "tech", "https://www.engadget.com/rss.xml"),
    Source("BleepingComputer", "tech-alerts", "https://www.bleepingcomputer.com/feed/"),

    // Science, hyped and careful, so the difference is measurable.
    Source("SciTechDaily", "sci-hype", "https://scitechdaily.com/feed/"),
    Source("New Atlas", "sci-hype", "https://newatlas.com/index.rss"),
    Source("ScienceAlert", "sci-hype", "https://www.sciencealert.com/feed"),
    Source("Nature", "sci-careful", "https://www.nature.com/nature.rss"),
    Source("ScienceDaily", "sci-hype", "https://www.sciencedaily.com/rss/all.xml"),

    // Climate and environment, a beat the panel did not cover until the 2026-10
    // beat audit. No lean pair yet: whether to add one, and which, is the
    // maintainer's call. "Guardian Environment" is the Guardian's environment
    // section, kept apart from its main feed so the beat can be read on its own.
    Source("Carbon Brief", "climate", "https://www.carbonbrief.org/feed/"),
    Source("Inside Climate News", "climate", "https://insideclimatenews.org/feed/"),
    Source("Grist", "climate", "https://grist.org/feed/"),
    Source("Guardian Environment", "climate", "https://www.theguardian.com/environment/rss"),

    // Money. "Your pension could…", "the change that could cost you thousands":
    // second-person hedge as a business model.
    Source("CNBC", "money", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114"),
    Source("Business Insider", "money", "https://www.businessinsider.com/rss"),
    Source("MarketWatch", "money", "https://feeds.content.dowjones.io/public/rss/mw_topstories"),
    Source("Investing.com", "money", "https://www.investing.com/rss/news.rss"),
    Source("Kiplinger", "money-hype", "https://www.kiplinger.com/feeds.xml"),

    // Local, a register nothing else in the panel represents.
    Source("Manchester Evening News", "local", "https://www.manchestereveningnews.co.uk/?service=rss"),

    // Sport beyond the BBC's notably plain house style.
    Source("BBC Sport", "sport", "https://feeds.bbci.co.uk/sport/rss.xml"),
    Source("ESPN", "sport", "https://www.espn.com/espn/rss/news"),

    // International Commonwealth broadcasters & investigative reporting (2026 expansion)
    Source("ABC News Australia", "public-intl", "https://www.abc.net.au/news/feed/51120/rss.xml"),
    Source("CBC News Canada", "public-intl", "https://www.cbc.ca/cmlink/rss-topstories"),
    Source("Sydney Morning Herald", "intl-broadsheet", "https://www.smh.com.au/rss/feed.xml"),
    Source("The Globe and Mail", "intl-broadsheet", "https://www.theglobeandmail.com/arc/outboundfeeds/rss/category/world/"),
    Source("ProPublica", "wire", "https://feeds.propublica.org/propublica/main"),
    Source("Financial Times", "money-careful", "https://www.ft.com/world?format=rss"),
    Source("Fortune", "money", "https://fortune.com/feed/"),
    Source("The Atlantic", "essay", "https://www.theatlantic.com/feed/all/"),

    // Loaded sources from connected device
    Source("TechCrunch", "tech", "https://techcrunch.com/feed/"),
    Source("Eurogamer", "gaming", "https://www.eurogamer.net/feed"),
    Source("PC Gamer", "gaming", "https://www.pcgamer.com/rss/"),
    Source("Rock Paper Shotgun", "gaming", "https://www.rockpapershotgun.com/feed"),
    Source("Quanta Magazine", "sci-careful", "https://www.quantamagazine.org/feed/"),
    Source("MIT Technology Review", "tech-careful", "https://www.technologyreview.com/topic/artificial-intelligence/feed"),
    Source("Redwood City Pulse", "local", "https://www.rwcpulse.com/feed/"),
    Source("Condé Nast Traveler", "lifestyle", "https://www.cntraveler.com/feed/rss"),
)

internal data class Source(val name: String, val kind: String, val url: String)

fun main(args: Array<String>) {
    val dryRun = args.contains("--dry-run") || args.contains("--no-write")
    val offline = args.contains("--offline")
    // --since=YYYY-MM-DD keeps only items published on or after that day, so a
    // run can test the rules on headlines written after they were.
    val since = parseSince(args)
    // Story pages are read from the cache; a dry run (the suite) or --offline
    // never fetches them, so a check-up run costs one request per feed.
    val fetchPages = !offline && !dryRun
    val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()
    val cacheDir = File(".cache/articles")

    // Phase 3: a link-carrying, detector-blind set for context labeling.
    //   --context-intake [--per-outlet=N] [--output=path]
    if (args.contains("--context-intake")) {
        if (dryRun) {
            println("--context-intake fetches pages and writes a file; it has no dry run.")
            return
        }
        ContextIntake.run(
            out = File(args.firstOrNull { it.startsWith("--output=") }?.substringAfter("--output=")
                ?: "build/context-intake.tsv"),
            // --sources=feeds.opml reads a reader app's exported list in place of the panel.
            panel = args.firstOrNull { it.startsWith("--sources=") }?.substringAfter("--sources=")
                ?.let { OpmlSources.read(File(it)) } ?: PANEL,
            perOutlet = args.firstOrNull { it.startsWith("--per-outlet=") }?.substringAfter("--per-outlet=")?.toIntOrNull()
                ?: ContextIntake.DEFAULT_PER_OUTLET,
            cacheDir = cacheDir,
            fetchFeed = { fetch(client, it) },
            fetchPage = { fetchHtml(client, it) },
            isBlocked = ::isBotBlocked,
            since = since,
        )
        return
    }

    val rows = mutableListOf<Row>()
    val dead = mutableListOf<String>()
    val itemsBySource = mutableMapOf<String, MutableList<FeedItem>>()
    val itemSourceMap = mutableMapOf<FeedItem, Source>()

    println()
    println("================================================================")
    println("  🌾 Haystack: Live News Harvester")
    println("  Gathering headlines from ${PANEL.size} news feeds (max $PER_SOURCE_CAP each)...")
    println("  Full 3-Layer Pipeline: Headline -> Summary -> Story Text")
    println("================================================================")
    if (dryRun) {
        println("  [DRY RUN MODE] Live harvest results will be analysed without saving to disk; story pages come from the cache only.")
    }
    if (offline) {
        println("  [OFFLINE MODE] Story pages come from the local cache only.")
    }
    if (since != null) {
        println("  [SINCE ${since.toString().take(10)}] Undated items and items published before then are skipped.")
    }
    println()

    for (source in PANEL) {
        val xml = runCatching { fetch(client, source.url) }
            .onFailure {
                println("  ${source.name.padEnd(24)} FAILED  ${it.message}")
                dead += source.name
            }
            .getOrNull() ?: continue

        val feed = runCatching { FeedParser.parse(xml) }
            .onFailure {
                println("  ${source.name.padEnd(24)} UNREADABLE  ${it.message}")
                dead += source.name
            }
            .getOrNull() ?: continue

        val taken = feed.items.filter { publishedSince(it, since) }.take(PER_SOURCE_CAP)
        for (item in taken) {
            val link = item.link?.trim().orEmpty()
            val id = if (link.isNotEmpty()) sha256Hex(link) else ""
            val cacheFile = if (id.isNotEmpty()) File(cacheDir, "$id.html") else null

            val html: String? = when {
                cacheFile != null && cacheFile.exists() -> runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
                link.isNotEmpty() && fetchPages && !isBotBlocked(link) -> {
                    fetchHtml(client, link)?.also { content ->
                        runCatching { cacheDir.mkdirs(); cacheFile?.writeText(content, Charsets.UTF_8) }
                    }
                }
                else -> null
            }

            val meta = if (!html.isNullOrBlank()) PageMetadata.extract(html, link) else null
            val storyText = if (!html.isNullOrBlank()) ArticleText.extract(html) else null
            val feedArticleType = ArticleTypeClassifier.classify(item.link, item.categories)
            val effectiveType = feedArticleType ?: meta?.articleType
            val typeSource = when {
                feedArticleType != null -> "feed"
                meta?.articleType != null -> "page"
                else -> ""
            }

            // 3-layer pipeline: Headline -> Summary -> Story
            val articleCtx = ArticleContext(
                articleType = effectiveType,
                pageSection = meta?.section,
                pageType = meta?.pageType,
                byline = meta?.author,
                summary = item.summary,
                storyText = storyText,
            )

            // Layer 1: Headline alone
            val rawResult = flagger.flag(item.title, ArticleContext(articleType = effectiveType), source.name)
            // Layer 2 & 3: Cross-referenced through Summary and Story body text
            val resolvedResult = flagger.flag(item.title, articleCtx, source.name)

            rows += Row(
                source = source,
                headline = item.title,
                summary = item.summary,
                result = rawResult,
                resolvedResult = resolvedResult,
                articleType = effectiveType?.id,
                link = item.link,
                storyText = storyText,
                pageSection = meta?.section,
                pageType = meta?.pageType,
                typeSource = typeSource,
            )
            itemSourceMap[item] = source
            itemsBySource.getOrPut(source.name) { mutableListOf() }.add(item)
        }
        val capped = if (feed.items.size > PER_SOURCE_CAP) "  (capped from ${feed.items.size})" else ""
        println("  ${source.name.padEnd(24)} ${taken.size} headlines$capped")
    }

    if (dead.isNotEmpty()) {
        println()
        println("  ${dead.size} feed(s) gave nothing this run: ${dead.joinToString(", ")}")
        println("  One bad run isn't proof a feed is dead: check again before removing it.")
    }

    if (rows.isEmpty()) {
        println("\nNothing fetched: check the network.")
        return
    }

    val markdownReportPath = args.firstOrNull { it.startsWith("--report-markdown=") }?.substringAfter("--report-markdown=")
    val storyGroups = StoryGrouper.group(itemsBySource)
    report(rows, storyGroups, itemSourceMap)
    if (markdownReportPath != null) {
        writeMarkdownReport(markdownReportPath, rows, storyGroups, itemSourceMap, dead)
    }

    val outputPath = args.firstOrNull { it.startsWith("--output=") }?.substringAfter("--output=")
        ?: if (args.contains("--merge-to-gold")) "fixtures/headlines/harvested.tsv"
           else "fixtures/headlines/harvested-live-intake.tsv"

    if (dryRun) {
        println("Fixture file: merge skipped because --dry-run was specified.")
        println()
    } else {
        merge(rows, outputPath)
    }
}

private data class Row(
    val source: Source,
    val headline: String,
    val summary: String?,
    val result: FlagResult,
    val resolvedResult: FlagResult,
    /** The article type the feed item or page gave, or null when it said nothing. */
    val articleType: String? = null,
    val link: String? = null,
    val storyText: String? = null,
    val pageSection: String? = null,
    val pageType: String? = null,
    val typeSource: String = "",
)

private fun report(
    rows: List<Row>,
    storyGroups: List<StoryGrouper.StoryGroup>,
    itemSourceMap: Map<FeedItem, Source>,
) {
    val flagged = rows.count { it.result.isFlagged }

    println()
    println("=".repeat(72))
    println("${rows.size} real headlines this run, $flagged flagged (${pct(flagged, rows.size)})")
    println("=".repeat(72))
    println()

    println("HOW MUCH OF EACH FEED GETS MARKED")
    println()
    println("  ${"source".padEnd(24)} ${"kind".padEnd(14)} total  flagged   loud")
    for ((source, group) in rows.groupBy { it.source }.entries.sortedBy { it.key.kind }) {
        val hit = group.count { it.result.isFlagged }
        val loud = group.count { it.result.level == FlagLevel.PROMINENT }
        println(
            "  ${source.name.padEnd(24)} ${source.kind.padEnd(14)} " +
                "${group.size.toString().padStart(5)}  ${pct(hit, group.size).padStart(7)}  " +
                pct(loud, group.size).padStart(5)
        )
    }

    evenHandednessCheck(rows)

    println()
    println("WHICH TECHNIQUES ACTUALLY TURN UP")
    println()
    val counts = Category.entries.associateWith { cat ->
        rows.count { cat in it.result.categories }
    }
    for ((cat, n) in counts.entries.sortedByDescending { it.value }) {
        println("  ${cat.displayName.padEnd(22)} ${n.toString().padStart(4)}  ${pct(n, rows.size)}")
    }

    println()
    println("THE LOUDEST HEADLINES FOUND")
    println()
    for (row in rows.sortedByDescending { it.result.score }.take(15)) {
        println("  ${"%.2f".format(row.result.score)}  [${row.source.name}] ${row.headline}")
        println("        ${row.result.hits.joinToString(", ") { "${it.category.displayName}: \"${it.matchedText}\"" }}")
    }

    // 1. 3-Layer Context Resolution check (Headline -> Summary -> Story)
    val withStory = rows.count { !it.storyText.isNullOrBlank() }
    val resolved = rows.filter { it.resolvedResult.resolvedContext != null }
    println()
    println("3-LAYER CONTEXT RESOLUTION (Headline -> Summary -> Story)")
    println()
    println("  Stories with extracted full body text: $withStory of ${rows.size} (${pct(withStory, rows.size)})")
    if (resolved.isNotEmpty()) {
        println("  ${resolved.size} headlines resolved curiosity gaps, caveats, or walk-backs across summary & story context:")
        for (r in resolved.take(10)) {
            val sourceLayer = if (r.summary?.contains(r.resolvedResult.resolvedContext.orEmpty()) == true) "Summary" else "Story"
            println("  [${r.source.name}] ($sourceLayer) ${r.headline}")
            println("        -> Resolved fact: \"${r.resolvedResult.resolvedContext}\"")
        }
    }

    // 2. Cross-Outlet Framing Contrasts check
    val contrasts = storyGroups.mapNotNull { group ->
        val items = group.items.map { item ->
            val src = itemSourceMap[item]?.name ?: "Unknown"
            val flag = rows.firstOrNull { it.source.name == src && it.headline == item.title }?.resolvedResult
                ?: rows.firstOrNull { it.headline == item.title }?.result
                ?: FlagResult.NOT_FLAGGED
            Triple(item.title, src, flag)
        }
        StoryFramingComparator.compare(items = items)
    }.filter { it.hasFramingContrast }

    if (contrasts.isNotEmpty()) {
        println()
        println("CROSS-OUTLET FRAMING CONTRASTS: How different outlets frame the exact same story")
        println()
        println("  ${contrasts.size} multi-outlet stories exhibited divergent editorial framing:")
        println()
        for (contrast in contrasts.take(5)) {
            val subject = contrast.sharedFactWords.take(5).joinToString(" ")
            println("  STORY: \"$subject\" (${contrast.comparisons.size} outlets)")
            for (c in contrast.comparisons) {
                val status = if (c.isCalmBaseline) "[CALM BASELINE]" else "[FLAGGED - ${c.flag.categories.joinToString { it.displayName }}]"
                println("    $status ${c.sourceName}: ${c.headline}")
                if (c.divergentEditorialWords.isNotEmpty()) {
                    println("      Injected sensational vocabulary: ${c.divergentEditorialWords.joinToString(", ")}")
                }
            }
            println()
        }
    }

    // 3. Retractions & Editorial Updates check
    val retractionPattern = Regex(
        """(?i)^(?:CORRECTION|RETRACTED|RETRACTION|CLARIFICATION|UPDATE|UPDATED|EDITOR'?S\s+NOTE|AMENDMENT)\b[:\s\-–—]|\[(?:CORRECTION|UPDATE|RETRACTION|CLARIFICATION)\]|\((?:CORRECTION|UPDATE|RETRACTION|CLARIFICATION)\)"""
    )
    val retractions = rows.filter { retractionPattern.containsMatchIn(it.headline) }
    if (retractions.isNotEmpty()) {
        println()
        println("RETRACTIONS & EDITORIAL UPDATES: Formal corrections or updated headlines in this run")
        println()
        println("  ${retractions.size} headline(s) contain explicit correction/update markers:")
        for (r in retractions) {
            val status = if (r.result.isFlagged) "[FLAGGED - ${r.result.categories.joinToString { it.displayName }}]" else "[CLEAN BASELINE]"
            println("    $status [${r.source.name}] ${r.headline}")
        }
        println()
    }

    println()
    println("A RANDOM SAMPLE OF WHAT WAS LEFT ALONE: check these for things we missed")
    println()
    for (row in rows.filter { !it.result.isFlagged }.shuffled().take(15)) {
        println("  [${row.source.name}] ${row.headline}")
    }
    println()
}

private fun writeMarkdownReport(
    path: String,
    rows: List<Row>,
    storyGroups: List<StoryGrouper.StoryGroup>,
    itemSourceMap: Map<FeedItem, Source>,
    dead: List<String>,
) {
    val file = File(path)
    file.parentFile?.mkdirs()

    val flagged = rows.count { it.result.isFlagged }
    val totals = "${rows.size} real headlines this run, $flagged flagged (${pct(flagged, rows.size)})"
    val deadFeeds = if (dead.isNotEmpty()) "${dead.size} feed(s) gave nothing: ${dead.joinToString(", ")}" else "All feeds responded cleanly."

    val contrasts = storyGroups.mapNotNull { group ->
        val items = group.items.map { item ->
            val src = itemSourceMap[item]?.name ?: "Unknown"
            val flag = rows.firstOrNull { it.source.name == src && it.headline == item.title }?.resolvedResult
                ?: rows.firstOrNull { it.headline == item.title }?.result
                ?: FlagResult.NOT_FLAGGED
            Triple(item.title, src, flag)
        }
        StoryFramingComparator.compare(items = items)
    }.filter { it.hasFramingContrast }

    val retractionPattern = Regex(
        """(?i)^(?:CORRECTION|RETRACTED|RETRACTION|CLARIFICATION|UPDATE|UPDATED|EDITOR'?S\s+NOTE|AMENDMENT)\b[:\s\-–—]|\[(?:CORRECTION|UPDATE|RETRACTION|CLARIFICATION)\]|\((?:CORRECTION|UPDATE|RETRACTION|CLARIFICATION)\)"""
    )
    val retractions = rows.filter { retractionPattern.containsMatchIn(it.headline) }

    file.printWriter().use { w ->
        w.println("## Weekly Newsroom Harvest & Framing Audit (${java.time.LocalDate.now()})")
        w.println()
        w.println("### Summary")
        w.println("* **Headline Intake:** $totals")
        w.println("* **Feed Health:** $deadFeeds")
        w.println()
        val withStory = rows.count { !it.storyText.isNullOrBlank() }
        val resolved = rows.filter { it.resolvedResult.resolvedContext != null }
        w.println("### 3-Layer Context Pipeline")
        w.println("* **Full Text Coverage:** $withStory of ${rows.size} stories (${pct(withStory, rows.size)})")
        w.println("* **Context Resolutions:** ${resolved.size} curiosity gaps or caveats resolved across summary/story text")
        w.println()
        w.println("### Cross-Outlet Framing Contrasts")
        if (contrasts.isEmpty()) {
            w.println("_None detected this run._")
        } else {
            w.println("```text")
            for (contrast in contrasts.take(5)) {
                val subject = contrast.sharedFactWords.take(5).joinToString(" ")
                w.println("STORY: \"$subject\" (${contrast.comparisons.size} outlets)")
                for (c in contrast.comparisons) {
                    val status = if (c.isCalmBaseline) "[CALM BASELINE]" else "[FLAGGED - ${c.flag.categories.joinToString { it.displayName }}]"
                    w.println("  $status ${c.sourceName}: ${c.headline}")
                    if (c.divergentEditorialWords.isNotEmpty()) {
                        w.println("    Injected sensational vocabulary: ${c.divergentEditorialWords.joinToString(", ")}")
                    }
                }
                w.println()
            }
            w.println("```")
        }
        w.println()
        w.println("### Retractions & Editorial Updates")
        if (retractions.isEmpty()) {
            w.println("_None detected this run._")
        } else {
            w.println("```text")
            for (r in retractions.take(15)) {
                val status = if (r.result.isFlagged) "[FLAGGED - ${r.result.categories.joinToString { it.displayName }}]" else "[CLEAN BASELINE]"
                w.println("  $status [${r.source.name}] ${r.headline}")
            }
            w.println("```")
        }
        w.println()
        w.println("---")
        w.println()
        w.println("### How to Review & Promote Intake Locally")
        w.println("To review these staged headlines blind and promote verified human labels to the gold benchmark:")
        w.println()
        w.println("```bash")
        w.println("git checkout harvest/weekly-intake")
        w.println("./gradlew :tools:label:run --args=\"--review-intake\"")
        w.println("./gradlew :tools:label:run --args=\"--promote-to-gold\"")
        w.println("./gradlew :tools:rule-eval:run --args=\"fixtures/headlines/harvested.tsv --fail-on-fp\"")
        w.println("```")
    }
    println("Markdown report written to: $path")
}

/**
 * The one check that says whether the rules have a political dialect problem.
 *
 * If outlets that lean opposite ways get flagged at wildly different rates, that
 * is either a real difference in how they write or a word list that learned one
 * side's vocabulary, and there is no way to tell which without looking. A gap
 * this tool can't explain is a reason to go and read the headlines, not a reason
 * to change a weight.
 */
private fun evenHandednessCheck(rows: List<Row>) {
    val pairs = listOf("uk-left" to "uk-right", "us-left" to "us-right")

    println()
    println("EVEN-HANDEDNESS: flagged rate on each side of a pair")
    println()
    for ((left, right) in pairs) {
        val l = rows.filter { it.source.kind == left }
        val r = rows.filter { it.source.kind == right }
        if (l.isEmpty() || r.isEmpty()) {
            println("  ${left.padEnd(10)} vs ${right.padEnd(10)}  not enough data this run")
            continue
        }
        val lp = l.count { it.result.isFlagged } * 100.0 / l.size
        val rp = r.count { it.result.isFlagged } * 100.0 / r.size
        val gap = kotlin.math.abs(lp - rp)
        val note = if (gap > 10) "  <-- worth reading the headlines behind this" else ""
        println(
            "  ${left.padEnd(10)} ${"%.0f%%".format(lp).padStart(5)} (${l.size})" +
                "   vs   ${right.padEnd(10)} ${"%.0f%%".format(rp).padStart(5)} (${r.size})" +
                "   gap ${"%.0f".format(gap)} pts$note"
        )
    }

    // A whole lean group mixes registers (the UK left group is mostly a
    // broadsheet, the right mostly tabloids), so compare like with like too.
    // These are the outlet study's pairs.
    println()
    println("  Like against like:")
    for (pair in REGISTER_PAIRS) {
        val l = rows.filter { it.source.name in pair.left }
        val r = rows.filter { it.source.name in pair.right }
        if (l.isEmpty() || r.isEmpty()) {
            println("  ${pair.name.padEnd(24)} not enough data this run")
            continue
        }
        val lp = l.count { it.result.isFlagged } * 100.0 / l.size
        val rp = r.count { it.result.isFlagged } * 100.0 / r.size
        val note = if (kotlin.math.abs(lp - rp) > 10) "  <-- worth reading the headlines behind this" else ""
        println(
            "  ${pair.name.padEnd(24)} left ${"%.0f%%".format(lp).padStart(5)} (${l.size})" +
                "   right ${"%.0f%%".format(rp).padStart(5)} (${r.size})" +
                "   gap ${"%.0f".format(kotlin.math.abs(lp - rp))} pts$note"
        )
    }
}

/**
 * Adds this run's headlines to the fixture file, keeping everything already
 * there.
 *
 * This used to overwrite the file. That made running the harvester twice
 * actively destructive: the second run would wipe both the previous day's
 * headlines and any labeling a person had done in between, which is the only
 * expensive part of the whole exercise. Since the plan is to run this daily for
 * a week or two, that had to change before the first extra run.
 *
 * Existing rows are copied through **exactly as they are**, including their
 * label and their REVIEW marker. A headline already in the file is never
 * re-judged by the detector, because a human may have overruled it.
 */
private val BLOCKED_DOMAINS = setOf(
    "theatlantic.com",
    "axios.com",
    "newstatesman.com",
    "marketwatch.com",
    "dailystar.co.uk",
    "manchestereveningnews.co.uk",
    "ft.com",
    "politico.com",
)

/** Matches the link's host, so "ft.com" catches www.ft.com but not microsoft.com. Same list as the benchmark's ArticleCache. */
private fun isBotBlocked(url: String): Boolean {
    val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase() ?: return false
    return BLOCKED_DOMAINS.any { host == it || host.endsWith(".$it") }
}

private fun sha256Hex(text: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun fetchHtml(client: HttpClient, url: String): String? = try {
    val request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .timeout(Duration.ofSeconds(15))
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    if (response.statusCode() in 200..299) {
        val body = response.body()
        val gzipped = body.size > 2 && body[0] == 0x1f.toByte() && body[1] == 0x8b.toByte()
        if (gzipped) {
            java.util.zip.GZIPInputStream(body.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            String(body, Charsets.UTF_8)
        }
    } else {
        null
    }
} catch (e: Exception) {
    null
}

private fun merge(rows: List<Row>, targetPath: String) {
    val out = File(targetPath)
    out.parentFile.mkdirs()

    val kindOf = PANEL.associate { it.name to it.kind }

    // Keep existing rows verbatim, keyed on the headline so a story that runs
    // for three days is stored once rather than three times.
    val existing = LinkedHashMap<String, Existing>()
    if (out.exists()) {
        for (line in out.readLines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val parts = line.split("\t")
            if (parts.size < 2) continue
            val headline = parts[1].trim()
            if (headline.isEmpty()) continue
            val sourceName = parts.getOrNull(2)?.trim()?.removePrefix("#")?.trim().orEmpty()
            // After the source: optional article type and how it was found, then
            // the REVIEW marker. Older rows have only the marker.
            val extras = parts.drop(3).map { it.trim() }
            val typed = extras.filter { it.isNotEmpty() && it != "REVIEW" }
            existing[headline] = Existing(
                label = parts[0].trim(),
                headline = headline,
                sourceName = sourceName,
                marker = if (extras.any { it == "REVIEW" }) "REVIEW" else "",
                articleType = typed.getOrNull(0).orEmpty(),
                typeSource = typed.getOrNull(1).orEmpty(),
            )
        }
    }

    val before = existing.size
    var added = 0

    // Read gold corpus to prevent duplicate intake into staging
    val goldFile = File("fixtures/headlines/harvested.tsv")
    val goldHeadlines = if (goldFile.exists() && targetPath != "fixtures/headlines/harvested.tsv") {
        goldFile.readLines().mapNotNull { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) null
            else line.split("\t").getOrNull(1)?.trim()
        }.toSet()
    } else {
        emptySet()
    }

    for (row in rows) {
        val headline = row.headline.replace('\t', ' ').replace('\n', ' ').trim()
        if (headline.isEmpty() || existing.containsKey(headline) || goldHeadlines.contains(headline)) continue

        existing[headline] = Existing(
            label = row.result.level.name,
            headline = headline,
            sourceName = row.source.name,
            marker = "REVIEW",
            articleType = row.articleType.orEmpty(),
            typeSource = row.typeSource,
        )
        added++
    }

    val all = existing.values.sortedWith(
        compareBy({ kindOf[it.sourceName] ?: "zz-unknown" }, { it.sourceName }, { it.headline })
    )
    val unreviewed = all.count { it.marker == "REVIEW" }

    out.printWriter().use { w ->
        w.println("# Real headlines. Last added to ${java.time.LocalDate.now()}.")
        w.println("# Target: $targetPath")
        w.println("#")
        w.println("# ${all.size} headlines, $unreviewed still unreviewed.")
        w.println("#")
        w.println("# The label column is THE DETECTOR'S OWN GUESS until a person changes it.")
        w.println("# Go through these, correct the ones it got wrong, and delete the REVIEW")
        w.println("# marker on every row you have actually looked at, including the ones it")
        w.println("# got right. Scoring the rules against un-reviewed rows proves nothing at")
        w.println("# all; the rules would simply be agreeing with themselves.")
        w.println("#")
        w.println("# Running the harvester again ADDS to this file. Your labels are kept.")
        w.println("#")
        w.println("# Columns: label, headline, source, [article type, how it was found,] REVIEW marker")
        w.println()
        for (r in all) {
            val type = if (r.articleType.isNotEmpty()) "\t${r.articleType}\t${r.typeSource}" else ""
            w.println("${r.label}\t${r.headline}\t# ${r.sourceName}$type\t${r.marker}".trimEnd())
        }
    }

    println()
    println("================================================================")
    println("  🌾 Haystack Summary")
    println("  Saved to:          $targetPath")
    println("  Previous count:    $before headlines")
    println("  Newly added:       $added headlines")
    println("  Total collection:  ${all.size} headlines ($unreviewed awaiting human review)")
    println("================================================================")
    println()
}

private data class Existing(
    val label: String,
    val headline: String,
    val sourceName: String,
    val marker: String,
    val articleType: String = "",
    val typeSource: String = "",
)

/**
 * Fetches one feed as text, decompressing it if the server sent it compressed.
 *
 * **Java's HttpClient does not decompress anything, ever.** It will send
 * `Accept-Encoding: gzip` for you, but the reply comes back as raw gzip bytes,
 * and reading those as a string produces mojibake that then fails XML parsing
 * with the thoroughly unhelpful message "Content is not allowed in prolog".
 *
 * The Independent looked like a dead feed for weeks because of this. It is not
 * dead: it returns a perfectly good 200 with `content-encoding: gzip`, and it
 * sends gzip **even when asked for `identity`**, which is why the polite fix of
 * asking nicely does not work and the bytes have to be sniffed instead.
 *
 * Sniffing the two magic bytes rather than trusting the header is deliberate:
 * the header is what a server claims, and this one already demonstrated it
 * ignores what it is asked for.
 */
private fun fetch(client: HttpClient, url: String): String {
    val request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", "MLE-feed-harvest/0.1")
        .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
        .timeout(Duration.ofSeconds(20))
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    require(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }

    val body = response.body()
    val gzipped = body.size > 2 &&
        body[0] == 0x1f.toByte() &&
        body[1] == 0x8b.toByte()

    return if (gzipped) {
        java.util.zip.GZIPInputStream(body.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } else {
        String(body, Charsets.UTF_8)
    }
}

private fun pct(part: Int, whole: Int) =
    if (whole == 0) "0%" else "${(part * 100.0 / whole).toInt()}%"
