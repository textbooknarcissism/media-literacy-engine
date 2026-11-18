package com.medialiteracy.ruleeval

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.FlagLevel
import java.io.File

/**
 * Scores the detection rules against the labeled headlines in fixtures/.
 *
 * Run it with:  ./gradlew :tools:rule-eval:run
 *
 * This is a developer tool, not part of the app. It exists so that changing a
 * word in the rule file produces a number instead of a guess. Tuning the rules
 * by reading them and feeling good about them does not work: the words that
 * cause trouble are almost never the ones you expect.
 *
 * **Rows still marked REVIEW are not scored.** Their label is whatever the
 * detector guessed when they were harvested, so scoring against them only ever
 * proves the rules agree with themselves. Before this was enforced, the tool
 * reported "100% correct, 0 wrongly flagged" over 482 headlines of which zero
 * had been looked at by a person, including several the detector plainly got
 * wrong.
 */
fun main(args: Array<String>) {
    val failOnFalsePositive = args.any { it == "--fail-on-fp" || it == "--strict" }
    val isProfile = args.contains("--profile")
    val path = args.firstOrNull { !it.startsWith("--") } ?: "fixtures/headlines/starter.tsv"
    val file = File(path)
    if (!file.exists()) {
        System.err.println("Could not find $path (looked in ${file.absolutePath})")
        return
    }

    val pack = RulePackLoader.loadDefault()
    val flagger = HeadlineFlagger(pack)

    // Run static linter check
    val lintReport = com.medialiteracy.flagging.RulePackLinter.lint(pack)
    if (lintReport.hasErrors) {
        System.err.println("WARNING: Rule pack contains static lint error(s):\n${lintReport.format()}")
    }

    var unreviewed = 0
    val cases = file.readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split("\t").map { it.trim() }
            val expected = runCatching { FlagLevel.valueOf(parts.first()) }.getOrNull()
                ?: return@mapNotNull null
            val headline = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (parts.any { it == "REVIEW" }) {
                unreviewed++
                return@mapNotNull null
            }
            val source = parts.getOrNull(2)?.removePrefix("#")?.trim().orEmpty().ifEmpty { "unknown" }
            Case(expected, headline, source)
        }

    if (isProfile) {
        runPerformanceProfiler(cases.map { it.headline }, flagger, pack)
        return
    }

    println()
    println("================================================================")
    println("  🔨 Hammer: Rule Accuracy & Performance Test")
    println("  Scoring rules against: $path")
    println("================================================================")
    println()

    if (unreviewed > 0) {
        println("  $unreviewed headlines skipped (marked REVIEW).")
        println("  Only human-verified headlines are scored to ensure honest testing.")
        println()
    }

    if (cases.isEmpty()) {
        println("  No reviewed headlines available to score in this file.")
        println("  Review headlines with Anvil (./gradlew anvil) to establish true test cases.")
        println()
        return
    }

    val wronglyFlagged = mutableListOf<Report>()
    val missed = mutableListOf<Report>()
    val wrongStrength = mutableListOf<Report>()
    var correct = 0

    for ((expected, headline, _) in cases) {
        val result = flagger.flag(headline)
        val report = Report(expected, result.level, result.score, headline, describe(result))

        when {
            result.level == expected -> correct++
            expected == FlagLevel.NONE -> wronglyFlagged += report
            result.level == FlagLevel.NONE -> missed += report
            else -> wrongStrength += report
        }
    }

    val total = cases.size
    val shouldBeLeftAlone = cases.count { it.expected == FlagLevel.NONE }
    val shouldBeMarked = total - shouldBeLeftAlone

    println("  Scored $total human-reviewed headlines:")
    println()
    println("  ✓ Correct Decisions:      ${pad(correct)}  (${percent(correct, total)})")
    println("  ✗ Honest News Flagged:    ${pad(wronglyFlagged.size)}  (False alarms: honest headlines marked by mistake)")
    println("  ✗ Attention Tricks Missed:${pad(missed.size)}  (Sensational tricks the engine slipped past)")
    println("  ~ Wrong Flag Intensity:   ${pad(wrongStrength.size)}  (Flagged, but too loud or too quiet)")
    println()
    println("  BOTTOM LINE ACCURACY:")
    println("  ---------------------")
    println(
        "  1. Real News Protection:  ${pad(shouldBeLeftAlone - wronglyFlagged.size)} of ${shouldBeLeftAlone.toString().padStart(3)}" +
            "  (${percent(shouldBeLeftAlone - wronglyFlagged.size, shouldBeLeftAlone)})" +
            "  <-- Honest headlines correctly left alone"
    )
    println(
        "  2. Clickbait Catch Rate:  ${pad(shouldBeMarked - missed.size)} of ${shouldBeMarked.toString().padStart(3)}" +
            "  (${percent(shouldBeMarked - missed.size, shouldBeMarked)})" +
            "  <-- Attention tricks successfully caught"
    )
    println()

    evenHandedness(cases, flagger)

    section("FALSE ALARMS (Honest headlines flagged by mistake: fix these first!)", wronglyFlagged)
    section("MISSED HEADLINES (Sensational tricks that slipped through)", missed)
    section("WRONG INTENSITY (Caught, but marked too loud or too quiet)", wrongStrength)

    if (wronglyFlagged.isEmpty() && missed.isEmpty() && wrongStrength.isEmpty()) {
        println("  ✓ Perfect score on this test set! Ready for more challenging headlines.")
        println()
    }

    if (failOnFalsePositive && wronglyFlagged.isNotEmpty()) {
        System.err.println()
        System.err.println("SAFETY FLOOR VIOLATION: ${wronglyFlagged.size} clean headline(s) were wrongly flagged!")
        kotlin.system.exitProcess(1)
    }
}

private data class Case(
    val expected: FlagLevel,
    val headline: String,
    /** The outlet, needed for the even-handedness check below. */
    val source: String,
)

/**
 * One left/right pair of outlets that ought to be flagged at about the same
 * rate if the word lists are even-handed.
 *
 * The pairs match the ones `tools/feed-harvest` deliberately built its panel
 * from. They are compared like with like (tabloid against tabloid, partisan
 * site against partisan site) because comparing a broadsheet against a tabloid
 * would measure register rather than politics and tell you nothing about lean.
 */
private data class Pair(
    val what: String,
    val left: List<String>,
    val right: List<String>,
)

private val PAIRS = listOf(
    Pair("UK tabloid", listOf("Daily Mirror", "Daily Star"), listOf("Daily Mail", "Daily Express")),
    Pair("UK broadsheet", listOf("The Guardian"), listOf("The Telegraph")),
    Pair("US partisan", listOf("Mother Jones"), listOf("Breitbart")),
    Pair("US television", listOf("MSNBC"), listOf("Fox News")),
)

/**
 * Prints how often each side of a matched political pair gets flagged.
 *
 * This runs on every check, on purpose. The single biggest risk to this feature
 * is not that it is wrong: it is that it is **wrong in one political
 * direction**, because that destroys the app's whole claim and nobody would
 * spot it from an accuracy number. Making it impossible to run the rule check
 * without seeing this is the cheapest defence there is.
 *
 * A gap here is a prompt to go and look, not a verdict. Two outlets can
 * genuinely differ, and the counts are small enough that a few headlines move
 * the percentage a long way.
 */
private fun evenHandedness(cases: List<Case>, flagger: HeadlineFlagger) {
    println("  EVEN-HANDEDNESS: matched pairs, flagged by the rules")
    println()

    for (pair in PAIRS) {
        val left = cases.filter { it.source in pair.left }
        val right = cases.filter { it.source in pair.right }

        if (left.isEmpty() || right.isEmpty()) {
            val missing = (if (left.isEmpty()) pair.left else pair.right).joinToString(", ")
            println("    ${pair.what.padEnd(15)} can't check: nothing harvested from $missing")
            continue
        }

        val leftRate = left.count { flagger.flag(it.headline).level != FlagLevel.NONE } * 100.0 / left.size
        val rightRate = right.count { flagger.flag(it.headline).level != FlagLevel.NONE } * 100.0 / right.size

        // The labels are shown next to the rules on purpose. A gap in what the
        // rules flag means nothing on its own: one side may simply do it more.
        // It only becomes a lean when the labels say the two sides are alike
        // and the rules disagree, so both numbers have to be visible together.
        val leftWanted = left.count { it.expected != FlagLevel.NONE } * 100.0 / left.size
        val rightWanted = right.count { it.expected != FlagLevel.NONE } * 100.0 / right.size

        val rulesGap = leftRate - rightRate
        val labelsGap = leftWanted - rightWanted
        val unexplained = kotlin.math.abs(rulesGap - labelsGap)

        println("    ${pair.what}  (left ${left.size} headlines, right ${right.size})")
        println(
            "      rules flag    left ${"%5.1f".format(leftRate)}%   right ${"%5.1f".format(rightRate)}%"
        )
        println(
            "      labels want   left ${"%5.1f".format(leftWanted)}%   right ${"%5.1f".format(rightWanted)}%"
        )
        println(
            "      unexplained gap ${"%.1f".format(unexplained)} points" +
                if (unexplained >= 10.0) "   <-- look at this" else ""
        )
        println()
    }
}

private data class Report(
    val expected: FlagLevel,
    val actual: FlagLevel,
    val score: Double,
    val headline: String,
    val why: String,
)

private fun section(title: String, reports: List<Report>) {
    if (reports.isEmpty()) return
    println(title)
    println("-".repeat(title.length))
    for (r in reports.sortedByDescending { r -> r.score }) {
        println("  Engine gave: ${r.actual} | Should be: ${r.expected} (score: ${"%.2f".format(r.score)})")
        println("    \"${r.headline}\"")
        if (r.why.isNotEmpty()) println("    Triggered by: ${r.why}")
        println()
    }
}

private fun describe(result: com.medialiteracy.model.FlagResult): String =
    result.hits.joinToString(", ") { "${it.category.displayName} -> \"${it.matchedText}\"" }

private fun pad(n: Int) = n.toString().padStart(3)

private fun percent(part: Int, whole: Int) =
    if (whole == 0) "0%" else "${(part * 100.0 / whole).toInt()}%"

private fun runPerformanceProfiler(
    headlines: List<String>,
    flagger: HeadlineFlagger,
    pack: com.medialiteracy.flagging.RulePack,
) {
    println()
    println("================================================================")
    println("  MLE: HeadlineFlagger Performance Profiler")
    println("  Rule pack version ${pack.version}, ${pack.categories.size} categories")
    println("================================================================")
    println()

    if (headlines.isEmpty()) {
        println("  No headlines available to benchmark.")
        return
    }

    println("  Warming up JVM with 1,000 iterations...")
    val warmupSample = headlines.take(50).ifEmpty { listOf("Sample headline for warmup test") }
    repeat(1000) { i ->
        flagger.flag(warmupSample[i % warmupSample.size])
    }

    println("  Running latency benchmarks across ${headlines.size} distinct headlines...")
    val latenciesNanos = LongArray(headlines.size)

    for (i in headlines.indices) {
        val hl = headlines[i]
        val start = System.nanoTime()
        flagger.flag(hl)
        latenciesNanos[i] = System.nanoTime() - start
    }

    latenciesNanos.sort()

    val totalNanos = latenciesNanos.sum()
    val avgMicros = (totalNanos.toDouble() / headlines.size) / 1_000.0
    val p50Micros = latenciesNanos[(headlines.size * 0.50).toInt()] / 1_000.0
    val p95Micros = latenciesNanos[(headlines.size * 0.95).toInt()] / 1_000.0
    val p99Micros = latenciesNanos[(headlines.size * 0.99).toInt()] / 1_000.0
    val maxMicros = latenciesNanos.last() / 1_000.0
    val throughputPerSec = if (totalNanos > 0) (headlines.size.toDouble() / (totalNanos / 1_000_000_000.0)).toInt() else 0

    println()
    println("  BENCHMARK RESULTS")
    println("  -----------------")
    println("  Sample size:        ${headlines.size} headlines")
    println("  Average latency:    ${"%.2f".format(avgMicros)} µs (${"%.4f".format(avgMicros / 1000.0)} ms)")
    println("  p50 (median):       ${"%.2f".format(p50Micros)} µs")
    println("  p95:                ${"%.2f".format(p95Micros)} µs")
    println("  p99:                ${"%.2f".format(p99Micros)} µs")
    println("  Max:                ${"%.2f".format(maxMicros)} µs")
    println("  Throughput:         $throughputPerSec headlines/second")
    println()

    val slaMicros = 1000.0 // 1.0 ms hard SLA ceiling
    val targetMicros = 500.0 // 0.5 ms target average
    if (avgMicros <= targetMicros && p99Micros <= slaMicros) {
        println("  ✓ SLA MET: Average (${"%.2f".format(avgMicros)} µs) < 500 µs target and p99 (${"%.2f".format(p99Micros)} µs) < 1.0 ms ceiling!")
    } else if (p99Micros <= slaMicros) {
        println("  ✓ SLA MET: p99 latency (${"%.2f".format(p99Micros)} µs) satisfies the < 1.0 ms SLA ceiling.")
    } else {
        println("  ✗ SLA EXCEEDED: p99 latency (${"%.2f".format(p99Micros)} µs) exceeded the 1.0 ms ceiling.")
    }
    println("================================================================")
    println()
}
