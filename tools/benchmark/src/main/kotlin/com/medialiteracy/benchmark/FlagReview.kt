package com.medialiteracy.benchmark

import com.medialiteracy.feeds.ArticleText
import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.FlagLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The monthly flag review (docs/teaching-reset-2026-10.md §6), MLE's main
 * measure: of the headlines it flagged, is each a clear case, and would the
 * highlight and question help a reader stop and think? A person answers both
 * on a review page; no model reads the headlines.
 *
 *   flag-review sample --intake <context intake tsv> --month YYYY-MM [--size 50]
 *   flag-review score --month YYYY-MM
 *   flag-review stories --month YYYY-MM          the cached stories as plain text, for reading
 *
 * A side check that is not the monthly measure (a reader's own outlets, say) takes a
 * labeled month and its own folder, so it never lands beside the monthly results:
 *   flag-review sample --intake <tsv> --month YYYY-MM-label --out build/<folder>
 *
 * `sample` flags every headline in a fresh harvest the way an app with the page
 * would (headline, article type, summary, and the story the harvest cached) and draws up to [SIZE] flagged ones, spread
 * across outlets, into docs/benchmark/flag-review/YYYY-MM/sample.json. The draw
 * is a hash of month and id, so the same intake gives the same sample.
 * `score` reads answers.json beside it and writes report.md and result.json;
 * the benchmark report shows the latest result first.
 */
object FlagReview {
    const val SIZE = 50
    const val TARGET = 0.90
    const val ROOT = "docs/benchmark/flag-review"

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class Hit(val technique: String, val name: String, val text: String, val start: Int, val end: Int, val question: String)

    @Serializable
    data class Item(
        val k: String,
        val id: String,
        val outlet: String,
        val articleType: String? = null,
        val link: String? = null,
        val headline: String,
        val level: String,
        val hits: List<Hit>,
    )

    @Serializable
    data class Sample(
        val month: String,
        val codebook: Int,
        val rulePack: Int,
        val intake: String,
        val headlines: Int,
        val flagged: Int,
        val items: List<Item>,
        /** The miss check: headlines MLE left alone, drawn the same way, for the reviewer to mark any obvious miss. Keys are m01, m02, ... */
        val unflagged: List<Item> = emptyList(),
    )

    /** The reviewer's answers, keyed by item `k`. Null means not answered yet. */
    @Serializable
    data class Answer(val clear: Boolean? = null, val helps: Boolean? = null, val note: String = "", val miss: Boolean? = null)

    @Serializable
    data class Answers(val month: String, val answers: Map<String, Answer>)

    @Serializable
    data class Result(
        val month: String,
        val codebook: Int,
        val rulePack: Int,
        val sampled: Int,
        val clearYes: Int,
        val clearAnswered: Int,
        val helpsYes: Int,
        val helpsAnswered: Int,
        /** The miss check: unflagged headlines the reviewer called an obvious miss, of those answered. */
        val missYes: Int = 0,
        val missAnswered: Int = 0,
    ) {
        val miss get() = Rate(missYes, missAnswered)
        val clear get() = Rate(clearYes, clearAnswered)
        val helps get() = Rate(helpsYes, helpsAnswered)
    }

    fun dir(month: String) = File(ROOT, month)

    /** A result's hits as shown on a page: offsets index into the headline, end exclusive. */
    internal fun hitsOf(result: com.medialiteracy.model.FlagResult): List<Hit> =
        result.hits.map { Hit(it.category.id, it.category.displayName, it.matchedText, it.range.first, it.range.last + 1, it.askYourself) }

    fun run(args: List<String>): Int {
        val command = args.firstOrNull()
        val flags = parseFlags(args.drop(1))
        val month = flags["--month"]?.takeIf { Regex("""\d{4}-\d{2}(-[a-z0-9]+)?""").matches(it) }
        if (month == null) {
            System.err.println("flag-review needs --month YYYY-MM")
            return 2
        }
        return when (command) {
            "sample" -> {
                val intake = flags["--intake"]?.let(::File)
                if (intake == null || !intake.exists()) {
                    System.err.println("flag-review sample needs --intake <context intake tsv> that exists")
                    return 2
                }
                val out = flags["--out"]?.let(::File)
                if (out == null && month.length > 7) {
                    System.err.println("flag-review sample with a labeled month needs --out <folder>, so it stays out of $ROOT")
                    return 2
                }
                sample(intake, month, flags["--size"]?.toIntOrNull() ?: SIZE, out ?: dir(month))
            }
            "score" -> score(month)
            "stories" -> stories(month)
            else -> {
                System.err.println("Use: flag-review sample|score|stories --month YYYY-MM")
                2
            }
        }
    }

    /** A context intake TSV read by its header row, so either intake layout works. */
    internal data class Row(val id: String, val outlet: String, val articleType: String?, val link: String?, val headline: String, val summary: String?, val pageSection: String?, val pageType: String?)

    internal fun readIntake(file: File): List<Row> {
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.firstOrNull { it.startsWith("id\t") }?.split('\t') ?: error("${file.path} has no 'id' header row")
        val col = header.withIndex().associate { (i, name) -> name to i }
        fun List<String>.at(name: String) = col[name]?.let { getOrNull(it) }?.trim()?.ifEmpty { null }
        return lines.filterNot { it.startsWith("id\t") }.map { it.split('\t') }.mapNotNull { p ->
            val id = p.at("id") ?: return@mapNotNull null
            val headline = p.at("headline") ?: return@mapNotNull null
            Row(id, p.at("outlet") ?: "", p.at("articleType")?.takeUnless { it == "unknown" }, p.at("link"), headline, p.at("summary"), p.at("pageSection"), p.at("pageType"))
        }
    }

    /**
     * Up to [size] items, ordered by a hash of month and id, taken one outlet
     * at a time so a busy outlet cannot fill the sample.
     */
    internal fun draw(flagged: List<Pair<Row, Item>>, month: String, size: Int): List<Pair<Row, Item>> {
        val byOutlet = flagged.sortedBy { sha256Hex("$month:${it.first.id}") }.groupBy { it.first.outlet }.values.map { ArrayDeque(it) }
        return buildList {
            while (size > this.size && byOutlet.any { it.isNotEmpty() }) {
                for (queue in byOutlet) if (queue.isNotEmpty() && size > this.size) add(queue.removeFirst())
            }
        }.sortedBy { sha256Hex("$month:${it.first.id}") }
    }

    /** The story the harvest cached for this row, as an app that has the page would pass it. */
    private fun storyText(id: String): String? = File(ArticleCache.CACHE_DIR, "$id.html")
        .takeIf { it.exists() }
        ?.let { runCatching { ArticleText.extract(it.readText(Charsets.UTF_8)) }.getOrNull() }

    private fun sample(intake: File, month: String, size: Int, outDir: File): Int {
        val pack = RulePackLoader.loadDefault()
        val flagger = HeadlineFlagger(pack)
        val rows = readIntake(intake).distinctBy { it.headline }
        val leftAlone = mutableListOf<Pair<Row, Item>>()
        val flagged = rows.mapNotNull { row ->
            val ctx = ArticleContext(
                articleType = ArticleType.fromId(row.articleType),
                summary = row.summary,
                pageSection = row.pageSection,
                pageType = row.pageType,
                storyText = storyText(row.id),
            )
            val got = flagger.flag(row.headline, ctx)
            if (got.level == FlagLevel.NONE) {
                leftAlone += row to Item(k = "", id = row.id, outlet = row.outlet, articleType = row.articleType, link = row.link, headline = row.headline, level = "NONE", hits = emptyList())
                return@mapNotNull null
            }
            row to Item(
                k = "",
                id = row.id,
                outlet = row.outlet,
                articleType = row.articleType,
                link = row.link,
                headline = row.headline,
                level = got.level.name,
                hits = hitsOf(got),
            )
        }
        val items = draw(flagged, month, size).mapIndexed { i, (_, item) -> item.copy(k = "f%02d".format(i + 1)) }
        val unflagged = draw(leftAlone, "$month:miss", size).mapIndexed { i, (_, item) -> item.copy(k = "m%02d".format(i + 1)) }
        val out = outDir.also { it.mkdirs() }
        val sample = Sample(month, CODEBOOK_VERSION, pack.version, intake.path.replace('\\', '/'), rows.size, flagged.size, items, unflagged)
        File(out, "sample.json").writeText(json.encodeToString(Sample.serializer(), sample) + "\n", Charsets.UTF_8)
        println("flag-review $month: ${rows.size} headlines, ${flagged.size} flagged, ${items.size} sampled, ${unflagged.size} left alone drawn for the miss check -> ${out.path}/sample.json")
        return 0
    }

    /**
     * Each sampled story the harvest cached, as plain text (the opening
     * [STORY_TEXT_CHARS] characters), in build/flag-review/YYYY-MM/stories/fNN.txt.
     * Reading these instead of whole web pages costs a session a fraction of
     * the tokens when the reviewer's notes need checking against the story.
     */
    private fun stories(month: String): Int {
        val sampleFile = File(dir(month), "sample.json")
        if (!sampleFile.exists()) {
            System.err.println("flag-review stories needs ${sampleFile.path}")
            return 2
        }
        val sample = json.decodeFromString(Sample.serializer(), sampleFile.readText(Charsets.UTF_8))
        val out = File("build/flag-review/$month/stories").also { it.mkdirs() }
        var written = 0
        for (item in sample.items) {
            val text = storyText(item.id) ?: continue
            File(out, "${item.k}.txt").writeText("${item.headline}\n${item.link ?: ""}\n\n${text.take(STORY_TEXT_CHARS)}\n", Charsets.UTF_8)
            written++
        }
        println("flag-review $month: $written of ${sample.items.size} stories -> ${out.path}")
        return 0
    }

    private const val STORY_TEXT_CHARS = 4000

    private fun score(month: String): Int {
        val d = dir(month)
        val sampleFile = File(d, "sample.json")
        val answersFile = File(d, "answers.json")
        if (!sampleFile.exists() || !answersFile.exists()) {
            System.err.println("flag-review score needs ${sampleFile.path} and ${answersFile.path}")
            return 2
        }
        val sample = json.decodeFromString(Sample.serializer(), sampleFile.readText(Charsets.UTF_8))
        val answers = json.decodeFromString(Answers.serializer(), answersFile.readText(Charsets.UTF_8)).answers
        val result = tally(sample, answers)
        File(d, "result.json").writeText(json.encodeToString(Result.serializer(), result) + "\n", Charsets.UTF_8)
        File(d, "report.md").writeText(report(sample, answers, result), Charsets.UTF_8)
        println("flag-review $month: clear case ${result.clear.formatWithInterval()}, helps a reader ${result.helps.formatWithInterval()} " +
            "(target ${Targets.percent(TARGET)} each) -> ${d.path}/report.md")
        return 0
    }

    internal fun tally(sample: Sample, answers: Map<String, Answer>): Result {
        val given = sample.items.mapNotNull { answers[it.k] }
        return Result(
            month = sample.month,
            codebook = sample.codebook,
            rulePack = sample.rulePack,
            sampled = sample.items.size,
            clearYes = given.count { it.clear == true },
            clearAnswered = given.count { it.clear != null },
            helpsYes = given.count { it.helps == true },
            helpsAnswered = given.count { it.helps != null },
            missYes = sample.unflagged.count { answers[it.k]?.miss == true },
            missAnswered = sample.unflagged.count { answers[it.k]?.miss != null },
        )
    }

    private fun verdict(rate: Rate): String = Targets.Result("", TARGET, rate).verdict.text

    private fun report(sample: Sample, answers: Map<String, Answer>, r: Result): String = buildString {
        appendLine("# Flag review, ${sample.month}")
        appendLine()
        appendLine("Codebook version ${sample.codebook}, rule pack ${sample.rulePack}. Intake `${sample.intake}`: ${sample.headlines} headlines, " +
            "${sample.flagged} flagged, ${sample.items.size} sampled, ${sample.items.count { it.k in answers }} answered by the reviewer. " +
            "Written by `flag-review score`; do not edit by hand.")
        appendLine()
        appendLine("| Question | Yes | Verdict against ${Targets.percent(TARGET)} |")
        appendLine("| :--- | :--- | :--- |")
        appendLine("| Is this a clear case? | ${r.clear.formatWithInterval()} | ${verdict(r.clear)} |")
        appendLine("| Would the highlight and question help a reader stop and think? | ${r.helps.formatWithInterval()} | ${verdict(r.helps)} |")
        appendLine()
        if (sample.unflagged.isNotEmpty()) {
            appendLine("**Miss check** (information, not a target): of ${r.missAnswered} headlines MLE left alone, the reviewer called ${r.missYes} an obvious miss (${r.miss.formatWithInterval()}).")
            appendLine()
            for (item in sample.unflagged.filter { answers[it.k]?.miss == true }) {
                val note = answers.getValue(item.k).note
                appendLine("- ${item.k}: ${item.headline} _(${item.outlet})_${if (note.isNotBlank()) " Reviewer: ${note.trim()}" else ""}")
            }
            appendLine()
        }
        val techniques = sample.items.flatMap { item -> item.hits.map { it.name to item } }.groupBy({ it.first }, { it.second })
        if (techniques.isNotEmpty()) {
            appendLine("**By technique named** (a headline with two techniques counts under both)")
            appendLine()
            appendLine("| Technique | Clear case | Helps a reader |")
            appendLine("| :--- | :--- | :--- |")
            for ((name, items) in techniques.entries.sortedByDescending { it.value.size }) {
                val given = items.distinct().mapNotNull { answers[it.k] }
                appendLine("| $name | ${Rate(given.count { it.clear == true }, given.count { it.clear != null }).formatWithInterval()} | " +
                    "${Rate(given.count { it.helps == true }, given.count { it.helps != null }).formatWithInterval()} |")
            }
            appendLine()
        }
        val noes = sample.items.filter { answers[it.k]?.let { a -> a.clear == false || a.helps == false } == true }
        if (noes.isNotEmpty()) {
            appendLine("**Answered no** (candidates for docs/teaching-reset-2026-10.md §7: a \"not clear\" is a precision fix, a \"doesn't help\" a wording fix)")
            appendLine()
            for (item in noes) {
                val a = answers.getValue(item.k)
                val which = listOfNotNull("not clear".takeIf { a.clear == false }, "doesn't help".takeIf { a.helps == false }).joinToString(", ")
                val hits = item.hits.joinToString { "${it.name} -> \"${it.text}\"" }
                appendLine("- ${item.k}, $which: ${item.headline} _(${item.outlet}; $hits)_${if (a.note.isNotBlank()) " Reviewer: ${a.note.trim()}" else ""}")
            }
            appendLine()
        }
        val notes = sample.items.filter { it !in noes && answers[it.k]?.note?.isNotBlank() == true }
        if (notes.isNotEmpty()) {
            appendLine("**Notes on headlines answered yes**")
            appendLine()
            for (item in notes) {
                val hits = item.hits.joinToString { "${it.name} -> \"${it.text}\"" }
                appendLine("- ${item.k}: ${item.headline} _(${item.outlet}; $hits)_ Reviewer: ${answers.getValue(item.k).note.trim()}")
            }
            appendLine()
        }
    }

    /** The latest scored month, for the top of the benchmark report. */
    fun latest(): Result? = File(ROOT).listFiles { f -> f.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { d -> File(d, "result.json").takeIf { it.exists() } }
        ?.let { json.decodeFromString(Result.serializer(), it.readText(Charsets.UTF_8)) }
}

fun flagReview(args: List<String>): Int = FlagReview.run(args)
