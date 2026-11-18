package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLinter
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.FlagLevel
import java.io.File
import kotlin.system.exitProcess

/**
 * Scores the rules against every benchmark dataset and writes one report.
 *
 *   ./gradlew :tools:benchmark:run                        everything, downloading external data
 *   ./gradlew :tools:benchmark:run --args="--offline"     committed data only
 *   --dataset <id>        only this dataset (repeatable)
 *   --report <path>       also write the markdown report to <path>
 *   --gate <baseline>     exit 1 if results regress against <baseline>
 *   --write-baseline <p>  record this run as the new baseline
 *   export-cases [--out build/ml/cases.jsonl]   every labeled row with the rules' verdict, to diff a rule change
 *   flag-review sample|score --month YYYY-MM     the monthly flag review (FlagReview.kt)
 *   practice export                              the edge cases as a practice deck (Practice.kt)
 *   flag "<headline>" ...                        what the rules find in each headline
 *   --context none|summary|story  what the engine sees besides the headline (default summary);
 *                         none measures what context adds, so don't gate or write a baseline with it
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull()?.takeUnless { it.startsWith("--") } ?: "run"
    val rest = if (args.firstOrNull() == command) args.drop(1) else args.toList()
    val code = when (command) {
        "run" -> runBenchmark(Options.parse(rest))
        "harvest" -> Harvest.run(File(rest.firstOrNull() ?: "fixtures/headlines/broadsheet-intake.tsv"))
        "label-batch" -> labelBatch(rest)
        "label-merge" -> labelMerge(rest)
        "export-cases" -> exportCases(rest)
        "flag-review" -> flagReview(rest)
        "practice" -> practice(rest)
        "flag" -> flagOne(rest)
        else -> {
            System.err.println("Unknown command '$command'")
            2
        }
    }
    exitProcess(code)
}

data class Options(
    val offline: Boolean = false,
    val only: Set<String> = emptySet(),
    val reportPath: String? = null,
    val gatePath: String? = null,
    val writeBaselinePath: String? = null,
    val context: ContextMode = ContextMode.SUMMARY,
) {
    fun wants(id: String) = only.isEmpty() || id in only

    companion object {
        fun parse(args: List<String>): Options {
            var o = Options()
            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--offline" -> o = o.copy(offline = true)
                    "--dataset" -> o = o.copy(only = o.only + args[++i])
                    "--report" -> o = o.copy(reportPath = args[++i])
                    "--gate" -> o = o.copy(gatePath = args[++i])
                    "--write-baseline" -> o = o.copy(writeBaselinePath = args[++i])
                    "--context" -> o = o.copy(context = ContextMode.fromId(args[++i]))
                    else -> throw IllegalArgumentException("Unknown option '$arg'")
                }
                i++
            }
            return o
        }
    }
}

fun runBenchmark(options: Options): Int {
    val pack = RulePackLoader.loadDefault()
    val flagger = HeadlineFlagger(pack)
    val chunks = mutableListOf(Report.header(pack.version))
    var failed = false

    // Sealed sets are read only so the memorization lint can protect their rows.
    val sealedHeadlines = mutableListOf<String>()
    val datasetChunks = mutableListOf<String>()
    val headlineReports = HEADLINE_DATASETS.filter { options.wants(it.id) }.mapNotNull { ds ->
        val file = File(ds.path)
        when {
            !file.exists() -> {
                datasetChunks += "## ${ds.id}\n\nskipped: ${ds.path} not found\n"
                null
            }
            ds.isSealed() -> {
                val cases = ds.read(file)
                sealedHeadlines += cases.map { it.headline }
                datasetChunks += Report.sealedSection(ds, cases.size)
                null
            }
            else -> HeadlineScorer.score(ds, ds.read(file), flagger, options.context).also { datasetChunks += Report.headlineSection(it) }
        }
    }
    val targets = Targets.check(headlineReports)
    if (options.only.isEmpty()) {
        FlagReview.latest()?.let { chunks += Report.flagReviewSection(it) }
        chunks += Report.targetsSection(targets, Targets.caught(headlineReports))
    }
    chunks += datasetChunks

    // Held-out headlines from freshly labeled data, against everything else
    // committed. Legacy (pre-2021) data is left out: it is only a regression check.
    val freshHeldout = headlineReports.filter { it.dataset.fresh }
        .flatMap { r -> r.scored.filter { it.part == SplitPart.HELDOUT }.map { it.case.headline } } + sealedHeadlines
    val otherHeadlines = headlineReports.filter { !it.dataset.legacyEvidence }
        .flatMap { r -> r.scored.filterNot { r.dataset.fresh && it.part == SplitPart.HELDOUT }.map { it.case.headline } }
    val knownBaseline = options.gatePath?.let { Baseline.readOrNull(File(it)) }
        ?: Baseline.readOrNull(File("fixtures/benchmark-baseline.json"))
    val memorisation = RulePackLinter.LintReport(
        RulePackLinter.lintMemorisation(
            pack,
            freshHeldout,
            otherHeadlines,
            grandfathered = knownBaseline?.knownTerms?.toSet().orEmpty(),
        ).issues + RulePackLinter.lintSignalMemorisation(
            pack,
            freshHeldout,
            otherHeadlines,
            grandfathered = knownBaseline?.knownSignals?.toSet().orEmpty(),
        ).issues,
    )
    chunks += Report.lintSection(memorisation)

    val cacheDir = File("fixtures/external")
    for (source in EXTERNAL_SOURCES.filter { options.wants(it.id) }) {
        val file = try {
            Fetch.cached(source, cacheDir, options.offline)
        } catch (e: Exception) {
            chunks += "## ${source.id}\n\nfailed: ${e.message}\n"
            continue
        }
        if (file == null) {
            chunks += "## ${source.id}\n\nskipped: ${source.id} not cached (run without --offline to download)\n"
            continue
        }
        chunks += when (source.kind) {
            SourceKind.TONE_BABE -> {
                val babe = ToneSources.readBabe(file)
                Report.toneSection(
                    source, ToneScorer.score(babe, flagger, hasWordLabels = true), hasWordLabels = true,
                    byGroup = ToneScorer.scoreByGroup(babe, flagger),
                )
            }
            SourceKind.TONE_CHECKTHAT ->
                Report.toneSection(source, ToneScorer.score(ToneSources.readCheckThat(file), flagger, hasWordLabels = false), hasWordLabels = false)
            SourceKind.TEASE_WEBIS22 ->
                Report.teaseSection(source, TeaseScorer.score(TeaseSources.readWebis22(file), flagger))
        }

    }

    val current = headlineReports.filter { !it.dataset.legacyEvidence }.associate { r ->
        r.dataset.id to DatasetBaseline(
            leftAloneTune = r.levels(SplitPart.TUNE).leftAlone,
            leftAloneHeldout = r.levels(SplitPart.HELDOUT).leftAlone,
            caughtHeldout = r.levels(SplitPart.HELDOUT).caught,
        )
    }
    options.writeBaselinePath?.let { path ->
        val terms = pack.categories.flatMap { it.terms + it.noticeTerms + it.softTerms }.map { it.lowercase() }.distinct().sorted()
        Baseline.write(File(path), Baseline(current, terms, RulePackLinter.signalLabels(pack)))
        println("Wrote baseline to $path")
    }

    val gateFile = options.gatePath?.let { File(it) } ?: File("fixtures/benchmark-baseline.json")
    val baseline = if (gateFile.exists()) Baseline.readOrNull(gateFile) else null
    val gateFailures = if (baseline != null) {
        Gate.check(baseline, current) + memorisation.errors.map { "Rule memorization (${it.categoryId}): ${it.message}" }
    } else {
        emptyList()
    }

    val markdown = chunks.joinToString("\n")
    val reportFile = File(options.reportPath ?: "docs/benchmark-results.md")
    reportFile.parentFile?.mkdirs()
    reportFile.writeText(markdown, Charsets.UTF_8)

    val bs = current["broadsheet-2026"]
    val hv = current["harvested"]
    val bt = current["beats-2026"]
    val cx = current["context-2026"]

    println()
    println("================================================================")
    println("  🛡️ Shield: Final Benchmark & Quality Scorecard")
    println("================================================================")
    if (bs != null) {
        println("  1. Broadsheet News Protection:   ${bs.leftAloneHeldout.format()} honest news left alone")
        println("     Catch Rate on Sensationalism: ${bs.caughtHeldout.format()} attention tricks caught")
    }
    if (hv != null) {
        println("  2. General News Protection:      ${hv.leftAloneHeldout.format()} honest news left alone")
        println("     Catch Rate on Sensationalism: ${hv.caughtHeldout.format()} attention tricks caught")
    }
    if (bt != null) {
        println("  3. Specialty Beats Protection:   ${bt.leftAloneHeldout.format()} honest news left alone")
    }
    if (cx != null) {
        println("  4. Context Evaluation (2026):    ${cx.leftAloneHeldout.format()} honest news left alone")
        println("     Catch Rate on Sensationalism: ${cx.caughtHeldout.format()} attention tricks caught")
    }
    if (options.only.isEmpty()) {
        println("  Left-alone targets (95% intervals):")
        for (t in targets) {
            println("     ${t.target} >= ${Targets.percent(t.goal)}: ${t.measured.formatWithInterval()}, ${t.verdict.text}")
        }
        println("  Caught (information, not a goal): ${Targets.caught(headlineReports).formatWithInterval()}")
    }
    println("----------------------------------------------------------------")
    if (gateFailures.isEmpty()) {
        println("  ✓ QUALITY GATE: PASSED (No regressions against baseline standard)")
    } else {
        println("  ✗ QUALITY GATE: FAILED (${gateFailures.size} regressions detected)")
        gateFailures.forEach { println("    - $it") }
        failed = true
    }
    println("  Detailed research results saved to: ${reportFile.path}")
    println("================================================================")
    println()

    return if (failed) 1 else 0
}
