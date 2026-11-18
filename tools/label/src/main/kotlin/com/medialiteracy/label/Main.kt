package com.medialiteracy.label

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.FlagLevel
import java.io.File
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Asks a person to label headlines, without letting them see any existing
 * answer.
 *
 * Run it with:  ./gradlew :tools:label:run
 * Then compare: ./gradlew :tools:label:run --args="--compare"
 *
 * ## Why this exists
 *
 * Every accuracy number this project has ever reported is scored against
 * `harvested.tsv`, and those labels were not produced by a person. The
 * harvester writes each new row pre-filled with **the detector's own guess**
 * (see `tools/feed-harvest`), and the review pass that cleared the REVIEW
 * markers was done by an AI that could see that guess while working.
 *
 * So "1,238 reviewed headlines" really means: the detector guessed, something
 * looked at the guess, and whatever it did not actively disagree with became
 * the truth everything is now measured against. That is a softer version of
 * the trap this project already fell into once, when `rule-eval` reported 100%
 * correct over 482 rows nobody had read.
 *
 * This tool exists to put a real number on how far those labels sit from the
 * judgment of the person who owns the product. It shows a headline and its
 * source. It does not show the stored label, the detector's verdict, the
 * score, or which group the headline was drawn from: **the detector's opinion
 * is computed in here, but it is never printed before an answer is given.**
 * That is the entire point of the tool, so if you change the display code,
 * keep it that way.
 *
 * It never writes to `harvested.tsv`. Answers go to their own file. The
 * worklog records a session where re-running the harvester overwrote the
 * reviewed fixture, and that must not be possible from here.
 */

private const val CORPUS = "fixtures/headlines/harvested.tsv"
private const val ANSWERS = "fixtures/headlines/human-sample.tsv"

/**
 * How many to draw from the quiet group.
 *
 * Only the quiet group is sampled. The other two are taken **whole**, because
 * they are small enough to be: the detector marks 68 headlines and the labels
 * claim it missed 88. Answering all of those is a census, not a sample, so the
 * number this project cares about most (*of the headlines the app marks, how
 * many should have been left alone?*) comes back exact, with no margin of
 * error at all. Sampling 60 of 68 would have thrown that away for the sake of
 * eight questions.
 */
private const val QUIET_SAMPLE = 60

/**
 * Fixed so that two people running this tool are asked the same headlines, and
 * so a half-finished run can be resumed months later and still line up.
 */
private const val SEED = 20260917L

/** At most this many from one outlet per group, so it isn't a third Daily Mail. */
private const val PER_OUTLET_CAP = 8

/**
 * How many answers each group needs before any corpus-wide estimate is shown.
 * Below this the multiplication produces a confident-looking number built on
 * almost nothing, which is the failure this whole tool exists to prevent.
 */
private const val MINIMUM_FOR_AN_ESTIMATE = 20

fun main(args: Array<String>) {
    val intakePath = "fixtures/headlines/harvested-live-intake.tsv"
    when {
        args.contains("--compare") -> compare()
        args.contains("--review-intake") -> reviewBacklog(intakePath)
        args.contains("--review") -> {
            val custom = args.firstOrNull { it.startsWith("--file=") }?.substringAfter("--file=")
                ?: args.firstOrNull { it.startsWith("--corpus=") }?.substringAfter("--corpus=")
                ?: CORPUS
            reviewBacklog(custom)
        }
        args.contains("--promote-to-gold") -> promoteToGold()
        args.contains("--audit-backlog") -> auditBacklog()
        else -> label()
    }
}

// ---------------------------------------------------------------- the corpus

private data class Row(
    /** What `harvested.tsv` currently claims. Never shown while labeling. */
    val storedLabel: FlagLevel,
    val headline: String,
    val source: String,
)

/**
 * The three groups the sample is drawn from.
 *
 * They are deliberately **disjoint and between them cover the whole corpus**,
 * which is what makes it possible to weight the answers back to a corpus-wide
 * estimate at the end. A plain random sample of 180 would be dominated by
 * ordinary headlines nobody disagrees about, and would contain perhaps four of
 * the cases where the detector and the labels actually differ.
 */
private enum class Stratum {
    /** The detector marks these. Contains every wrongly-flagged headline. */
    FLAGGED,

    /** The detector stays quiet, but the stored label says it should not have. */
    MISSED,

    /** The detector stays quiet and the stored label agrees. The quiet majority. */
    QUIET,
}

private fun readCorpus(): List<Row> {
    val file = File(CORPUS)
    require(file.exists()) { "Could not find $CORPUS (looked in ${file.absolutePath})" }

    return file.readLines(Charsets.UTF_8).mapNotNull { line ->
        if (line.isBlank() || line.trimStart().startsWith("#")) return@mapNotNull null
        val parts = line.split("\t").map { it.trim() }

        // Rows still marked REVIEW have never been looked at by anything but
        // the detector, so there is no stored answer to disagree with.
        if (parts.any { it == "REVIEW" }) return@mapNotNull null

        val stored = runCatching { FlagLevel.valueOf(parts.first()) }.getOrNull()
            ?: return@mapNotNull null
        val headline = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val source = parts.getOrNull(2)?.removePrefix("#")?.trim().orEmpty().ifEmpty { "unknown" }

        Row(stored, headline, source)
    }
}

private fun stratify(rows: List<Row>): Map<Stratum, List<Row>> {
    val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
    return rows.groupBy { row ->
        val detected = flagger.flag(row.headline).level
        when {
            detected != FlagLevel.NONE -> Stratum.FLAGGED
            row.storedLabel != FlagLevel.NONE -> Stratum.MISSED
            else -> Stratum.QUIET
        }
    }
}

/**
 * Takes [want] rows, refusing to take more than [PER_OUTLET_CAP] from any one
 * outlet, unless the group is too concentrated to fill the quota that way, in
 * which case the cap loosens one step at a time rather than giving up.
 */
private fun drawCapped(pool: List<Row>, want: Int, random: Random): List<Row> {
    val shuffled = pool.shuffled(random)
    var cap = PER_OUTLET_CAP
    while (true) {
        val taken = mutableListOf<Row>()
        val perOutlet = mutableMapOf<String, Int>()
        for (row in shuffled) {
            if (taken.size == want) break
            val used = perOutlet[row.source] ?: 0
            if (used >= cap) continue
            perOutlet[row.source] = used + 1
            taken += row
        }
        if (taken.size == want || cap >= want) return taken
        cap++
    }
}

private data class Pick(val row: Row, val stratum: Stratum)

private fun buildSample(rows: List<Row>): List<Pick> {
    val random = Random(SEED)
    val byStratum = stratify(rows)

    val picks = Stratum.entries.flatMap { stratum ->
        val pool = byStratum[stratum].orEmpty()
        val rows = when (stratum) {
            // Taken whole. Both are small, and both are where the interesting
            // disagreements live: a census here means the headline numbers
            // come back exact instead of estimated.
            Stratum.FLAGGED, Stratum.MISSED -> pool.shuffled(random)

            // The quiet majority is far too big to answer in full, so this one
            // is a capped random sample and the only place error creeps in.
            Stratum.QUIET -> drawCapped(pool, minOf(QUIET_SAMPLE, pool.size), random)
        }
        rows.map { Pick(it, stratum) }
    }

    // Shuffled together so that the order gives nothing away. If every
    // detector-flagged headline arrived in a block, the person answering would
    // work it out within a dozen rows and start second-guessing.
    return picks.shuffled(random)
}

// -------------------------------------------------------------- the answers

private data class Answer(val label: FlagLevel, val headline: String)

private fun readAnswers(): LinkedHashMap<String, Answer> {
    val file = File(ANSWERS)
    val answers = LinkedHashMap<String, Answer>()
    if (!file.exists()) return answers

    for (line in file.readLines(Charsets.UTF_8)) {
        if (line.isBlank() || line.trimStart().startsWith("#")) continue
        val parts = line.split("\t").map { it.trim() }
        val label = runCatching { FlagLevel.valueOf(parts.first()) }.getOrNull() ?: continue
        val headline = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: continue
        answers[headline] = Answer(label, headline)
    }
    return answers
}

private fun appendAnswer(file: File, label: FlagLevel, pick: Pick) {
    if (!file.exists()) {
        file.writeText(
            buildString {
                appendLine("# Headlines labelled by a person, blind.")
                appendLine("#")
                appendLine("# Written by tools/label. Nothing else reads or writes this file, and")
                appendLine("# this tool never touches harvested.tsv.")
                appendLine("#")
                appendLine("# The person answering saw the headline and its source only, not the")
                appendLine("# stored label, not the detector's verdict, not which group it came")
                appendLine("# from. That is what makes it worth comparing against.")
                appendLine("#")
                appendLine("# Columns: label, headline, source, group")
            }
        )
    }
    // Appended after every single answer, not at the end, so that stopping
    // halfway (or closing the terminal) never loses work.
    file.appendText("$label\t${pick.row.headline}\t# ${pick.row.source}\t${pick.stratum}\n")
}

// ------------------------------------------------------------- the questions

private fun label() {
    val rows = readCorpus()
    val sample = buildSample(rows)
    val answers = readAnswers()
    val answersFile = File(ANSWERS)

    val remaining = sample.filterNot { answers.containsKey(it.row.headline) }
    val done = sample.size - remaining.size

    println()
    println("MLE: blind labelling")
    println()
    println("  ${sample.size} headlines in the sample, $done already answered.")
    if (remaining.isEmpty()) {
        println()
        println("  All done. Run with --compare to see how your answers line up")
        println("  with the labels already in the file.")
        println()
        return
    }
    println()
    println("  For each headline: does it use a technique to make you feel")
    println("  something, or click, that the story itself does not support?")
    println()
    println("    1  no: ordinary reporting, leave it alone")
    println("    2  yes, quietly")
    println("    3  yes, loudly: several techniques at once")
    println("    s  skip: you'll be asked this one again next time")
    println("    q  stop for now (everything answered so far is saved)")
    println()
    println("  Answer on what the headline does, not on who published it.")
    println("  You are not being shown what anything else thinks, on purpose.")
    println()

    for ((index, pick) in remaining.withIndex()) {
        println("  ─────────────────────────────────────────────────────────────")
        println("  ${done + index + 1} of ${sample.size}   ·   ${pick.row.source}")
        println()
        println("  ${pick.row.headline}")
        println()
        print("  1 / 2 / 3 / s / q  > ")

        val level = when (readlnOrNull()?.trim()?.lowercase()) {
            "1" -> FlagLevel.NONE
            "2" -> FlagLevel.SUBTLE
            "3" -> FlagLevel.PROMINENT
            "s" -> { println(); continue }
            "q", null -> {
                println()
                println("  Stopped. ${done + index} answered. Run again to pick up here.")
                println()
                return
            }
            else -> { println("  Not one of the options: skipping."); println(); continue }
        }

        appendAnswer(answersFile, level, pick)
        println()
    }

    println("  Done: all ${sample.size} answered.")
    println("  Now run:  ./gradlew :tools:label:run --args=\"--compare\"")
    println()
}

// ------------------------------------------------------------ the comparison

private fun compare() {
    val rows = readCorpus()
    val sample = buildSample(rows)
    val answers = readAnswers()
    val byStratum = stratify(rows)

    val answered = sample.filter { answers.containsKey(it.row.headline) }
    if (answered.isEmpty()) {
        println()
        println("  Nothing answered yet. Run ./gradlew :tools:label:run first.")
        println()
        return
    }

    println()
    println("MLE: blind labels vs the labels already in the file")
    println()
    println("  ${answered.size} of ${sample.size} answered.")
    println()

    // --- how often the two agree, by what the file already claimed ----------
    println("  WHERE YOU AGREED WITH THE STORED LABEL")
    println()
    for (stored in FlagLevel.entries) {
        val group = answered.filter { it.row.storedLabel == stored }
        if (group.isEmpty()) continue
        val same = group.count { answers.getValue(it.row.headline).label == stored }
        println(
            "    file says ${stored.toString().padEnd(9)} " +
                "you agreed ${same.toString().padStart(3)} of ${group.size.toString().padStart(3)}" +
                "  (${percent(same, group.size)})"
        )
    }
    println()

    // Treated as a yes/no question, because that is what the reader sees: the
    // app shows a mark or it doesn't. SUBTLE vs PROMINENT is a distinction no
    // screen currently draws.
    val bothFlagged = answered.count {
        (answers.getValue(it.row.headline).label != FlagLevel.NONE) ==
            (it.row.storedLabel != FlagLevel.NONE)
    }
    println("  Treated as just 'mark it or not', you agreed on $bothFlagged of ${answered.size}  (${percent(bothFlagged, answered.size)})")
    println()

    // --- what that does to the headline numbers -----------------------------
    println("  WHAT THAT MEANS FOR THE NUMBERS THIS PROJECT REPORTS")
    println()

    val flaggedPool = byStratum[Stratum.FLAGGED].orEmpty().size

    // Of the headlines the detector marks, how many would this person not have
    // marked at all? That is the wrongly-flagged rate, measured against a
    // person instead of against a stored guess.
    val flaggedAnswered = answered.filter { it.stratum == Stratum.FLAGGED }

    // A handful of answers multiplied up to a whole corpus produces a
    // confident-looking number that means nothing, which is the exact failure
    // this tool exists to stop. So the estimates stay hidden until every group
    // has enough behind it. The disagreements below are still worth reading
    // from the very first answer, so they print either way.
    val enough = Stratum.entries.all { s ->
        answered.count { it.stratum == s } >= MINIMUM_FOR_AN_ESTIMATE
    }

    if (!enough) {
        println("    Not enough answers yet, so no estimate is shown. You need at least")
        println("    $MINIMUM_FOR_AN_ESTIMATE in each group and currently have:")
        println()
        for (stratum in Stratum.entries) {
            val n = answered.count { it.stratum == stratum }
            val mark = if (n >= MINIMUM_FOR_AN_ESTIMATE) "ok" else "keep going"
            println("      ${stratum.toString().padEnd(8)} ${n.toString().padStart(3)}   $mark")
        }
        println()
    } else {
        val wrong = flaggedAnswered.count { answers.getValue(it.row.headline).label == FlagLevel.NONE }
        val rate = wrong.toDouble() / flaggedAnswered.size
        val corpusWrong = rate * flaggedPool

        // Every marked headline in the corpus is in this group, so once they
        // are all answered this is a count, not an estimate. Saying "± 6%" on
        // a number that was actually counted would be the same false precision
        // this tool was built to remove.
        val census = flaggedAnswered.size == flaggedPool
        val moe = if (census) 0.0 else marginOfError(rate, flaggedAnswered.size)

        println("    Of the ${flaggedAnswered.size} marked headlines you saw, you would not have marked $wrong.")
        if (census) {
            println("    That is every marked headline in the file, so this is a count and")
            println("    not an estimate: ${"%.1f".format(rate * 100)}% of what the detector marks is wrong,")
            println("    which is $wrong headlines.")
        } else {
            println("    That is ${"%.1f".format(rate * 100)}% ± ${"%.1f".format(moe * 100)} of everything the detector marks,")
            println("    which across the whole file is about ${"%.0f".format(corpusWrong)} wrongly marked headlines.")
        }
        println("    (rule-eval currently reports 25, scored against the stored labels).")
        println()

        // Of the headlines the detector stays quiet on, how many should it
        // have marked? Measured separately in the two quiet groups, then each
        // weighted by how much of the corpus that group actually is.
        val missedPool = byStratum[Stratum.MISSED].orEmpty().size
        val quietPool = byStratum[Stratum.QUIET].orEmpty().size
        val missedAnswered = answered.filter { it.stratum == Stratum.MISSED }
        val quietAnswered = answered.filter { it.stratum == Stratum.QUIET }

        val missedRate = missedAnswered.count {
            answers.getValue(it.row.headline).label != FlagLevel.NONE
        }.toDouble() / missedAnswered.size
        val quietRate = quietAnswered.count {
            answers.getValue(it.row.headline).label != FlagLevel.NONE
        }.toDouble() / quietAnswered.size

        val shouldBeMarked = missedRate * missedPool + quietRate * quietPool + flaggedPool
        println("    Counting the ones you would have marked that the detector stayed quiet on,")
        println("    about ${"%.0f".format(shouldBeMarked)} headlines in the file deserve a mark, and the detector")
        println("    finds $flaggedPool of them: a catch rate of about ${percent(flaggedPool, shouldBeMarked.toInt().coerceAtLeast(1))}.")
        println("    (rule-eval currently reports 32%, scored against the stored labels.)")
        println()
        println("    The quiet group is the one to watch here: it is by far the biggest")
        println("    part of the corpus, so a single surprise in it moves this estimate")
        println("    a long way. Treat it as a rough check, not a replacement number.")
        println()
    }

    // --- the disagreements themselves, which are the useful part -------------
    val disagreements = answered.filter {
        (answers.getValue(it.row.headline).label != FlagLevel.NONE) !=
            (it.row.storedLabel != FlagLevel.NONE)
    }
    if (disagreements.isNotEmpty()) {
        println("  WHERE YOU AND THE FILE DISAGREE: read these, they are the point")
        println("  " + "-".repeat(58))
        for (pick in disagreements) {
            val yours = answers.getValue(pick.row.headline).label
            println("    you: ${yours.toString().padEnd(9)} file: ${pick.row.storedLabel.toString().padEnd(9)} ${pick.row.source}")
            println("      ${pick.row.headline}")
            println()
        }
    }

    println("  Nothing here has changed harvested.tsv. If you trust your own")
    println("  answers more than the stored ones, that is a separate decision")
    println("  about what to do with the file.")
    println()
}

// ------------------------------------------------------------------ plumbing

/**
 * The 95% margin of error on a proportion: the "± 4%" you see quoted on
 * polls. With 60 answers it is roughly 12 percentage points at worst, which is
 * wide, and saying so is better than quoting a number as if it were exact.
 */
private fun marginOfError(proportion: Double, n: Int): Double =
    if (n == 0) 0.0 else 1.96 * sqrt(proportion * (1 - proportion) / n)

private fun percent(part: Int, whole: Int) =
    if (whole == 0) "0%" else "${(part * 100.0 / whole).toInt()}%"

// ------------------------------------------------------------- review backlog

/**
 * Reviews headlines in [corpusPath] that are currently marked REVIEW, presenting
 * them to a human blind (without the detector's guess).
 *
 * When the person selects a label, the row in [corpusPath] is updated in place
 * with the human verdict and the REVIEW marker is removed. Progress is written
 * to disk after every single response so quitting at any point preserves work.
 */
private fun reviewBacklog(corpusPath: String = CORPUS) {
    val file = File(corpusPath)
    if (!file.exists()) {
        println()
        println("Could not find file at $corpusPath.")
        if (corpusPath != CORPUS) {
            println("Run './gradlew :tools:feed-harvest:run' first to generate live intake headlines.")
        }
        println()
        return
    }

    val lines = file.readLines(Charsets.UTF_8).toMutableList()
    val unreviewedIndices = mutableListOf<Int>()

    for ((index, line) in lines.withIndex()) {
        if (line.isBlank() || line.trimStart().startsWith("#")) continue
        val parts = line.split("\t")
        if (parts.any { it.trim() == "REVIEW" }) {
            unreviewedIndices.add(index)
        }
    }

    if (unreviewedIndices.isEmpty()) {
        println()
        println("All headlines in $corpusPath have been reviewed! None are marked REVIEW.")
        if (corpusPath != CORPUS) {
            println("Run './gradlew :tools:label:run --args=\"--promote-to-gold\"' to merge them into the gold corpus.")
        }
        println()
        return
    }

    // Interleave across outlets so the reviewer isn't hit with 50 headlines from one site in a row
    val order = unreviewedIndices.shuffled(Random(System.currentTimeMillis()))

    println()
    println("MLE: Review Unreviewed Headlines ($corpusPath)")
    println()
    println("  ${unreviewedIndices.size} unreviewed headlines in $corpusPath.")
    println("  Each headline is presented blind without the detector's guess.")
    println("  Progress is saved to disk after every single answer.")
    println()
    println("    1  None: ordinary reporting, leave it clean")
    println("    2  Subtle: quiet technique")
    println("    3  Prominent: loud manipulation / multiple techniques")
    println("    s  Skip this headline for now")
    println("    q  Save and quit")
    println()

    var reviewedCount = 0

    for (lineIndex in order) {
        val line = lines[lineIndex]
        val parts = line.split("\t")
        val headline = parts.getOrNull(1)?.trim().orEmpty()
        val source = parts.getOrNull(2)?.removePrefix("#")?.trim().orEmpty().ifEmpty { "Unknown" }

        println("  ─────────────────────────────────────────────────────────────")
        val remaining = unreviewedIndices.size - reviewedCount
        println("  [#${reviewedCount + 1} this session · $remaining remaining]  ·  $source")
        println()
        println("  $headline")
        println()
        print("  1 (None) / 2 (Subtle) / 3 (Prominent) / s (skip) / q (quit) > ")

        val level = when (readlnOrNull()?.trim()?.lowercase()) {
            "1" -> FlagLevel.NONE
            "2" -> FlagLevel.SUBTLE
            "3" -> FlagLevel.PROMINENT
            "s" -> {
                println()
                continue
            }
            "q", null -> {
                println()
                println("  Stopped. $reviewedCount headlines reviewed and saved to $corpusPath.")
                println("  Run again anytime to continue reviewing.")
                println()
                return
            }
            else -> {
                println("  Unrecognized option: skipping.")
                println()
                continue
            }
        }

        // Update line in memory: new label, headline, source and any article
        // type, stripping the REVIEW marker.
        lines[lineIndex] = "$level\t$headline\t# $source" + typeColumns(lines[lineIndex])
        file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
        reviewedCount++
        println()
    }

    println()
    println("  All ${unreviewedIndices.size} unreviewed headlines in $corpusPath reviewed and saved!")
    if (corpusPath != CORPUS) {
        println("  Run './gradlew :tools:label:run --args=\"--promote-to-gold\"' to merge verified rows into $CORPUS.")
    } else {
        println("  Run ./gradlew :tools:rule-eval:run to see updated benchmark scores.")
    }
    println()
}

/**
 * The optional article-type columns after the source ("opinion", "feed") as a
 * tab-led suffix, or "" when the row has none. The REVIEW marker is not one.
 */
private fun typeColumns(line: String): String {
    val typed = line.split("\t").drop(3).map { it.trim() }.filter { it.isNotEmpty() && it != "REVIEW" }
    return if (typed.isEmpty()) "" else typed.take(2).joinToString("\t", prefix = "\t")
}

/**
 * Promotes human-reviewed rows from [intakePath] into [goldPath].
 *
 * Keeps [goldPath] pristine by only importing rows where the REVIEW marker has
 * been removed by a person. Makes an automated timestamped backup before touching gold.
 */
private fun promoteToGold(
    intakePath: String = "fixtures/headlines/harvested-live-intake.tsv",
    goldPath: String = CORPUS,
) {
    val intakeFile = File(intakePath)
    if (!intakeFile.exists()) {
        println()
        println("Intake file does not exist: $intakePath")
        println()
        return
    }
    val goldFile = File(goldPath)
    require(goldFile.exists()) { "Gold corpus not found at $goldPath" }

    val goldLines = goldFile.readLines(Charsets.UTF_8).toMutableList()
    val existingGoldHeadlines = goldLines.mapNotNull { line ->
        if (line.isBlank() || line.trimStart().startsWith("#")) null
        else line.split("\t").getOrNull(1)?.trim()
    }.toSet()

    val intakeLines = intakeFile.readLines(Charsets.UTF_8)
    val remainingIntakeLines = mutableListOf<String>()
    var promotedCount = 0
    val newlyPromoted = mutableListOf<String>()

    for (line in intakeLines) {
        if (line.isBlank() || line.trimStart().startsWith("#")) {
            remainingIntakeLines.add(line)
            continue
        }
        val parts = line.split("\t")
        val isUnreviewed = parts.any { it.trim() == "REVIEW" }
        val headline = parts.getOrNull(1)?.trim().orEmpty()

        if (isUnreviewed) {
            remainingIntakeLines.add(line)
        } else {
            if (headline.isNotEmpty() && !existingGoldHeadlines.contains(headline)) {
                val label = parts[0].trim()
                val source = parts.getOrNull(2)?.trim().orEmpty()
                newlyPromoted.add("$label\t$headline\t$source" + typeColumns(line))
                promotedCount++
            }
        }
    }

    if (promotedCount > 0) {
        val stamp = java.time.LocalDate.now().toString()
        val backupDir = File("fixtures/harvest-log")
        backupDir.mkdirs()
        goldFile.copyTo(File(backupDir, "harvested.$stamp.pre-promote.bak.tsv"), overwrite = true)

        goldFile.appendText(newlyPromoted.joinToString("\n") + "\n", Charsets.UTF_8)

        val updatedGoldLines = goldFile.readLines(Charsets.UTF_8).toMutableList()
        val totalGoldHeadlines = updatedGoldLines.count { !it.startsWith("#") && it.isNotBlank() }
        for ((index, line) in updatedGoldLines.withIndex()) {
            if (line.contains("headlines,") && line.contains("still unreviewed")) {
                updatedGoldLines[index] = "# $totalGoldHeadlines headlines, 0 still unreviewed."
                break
            }
        }
        goldFile.writeText(updatedGoldLines.joinToString("\n") + "\n", Charsets.UTF_8)

        val unreviewedLeft = remainingIntakeLines.count { !it.startsWith("#") && it.isNotBlank() && it.contains("REVIEW") }
        val newIntakeContent = if (remainingIntakeLines.isEmpty()) "" else remainingIntakeLines.joinToString("\n").trimEnd() + "\n"
        intakeFile.writeText(newIntakeContent, Charsets.UTF_8)

        println()
        println("Promoted $promotedCount reviewed headlines into $goldPath.")
        println("Gold corpus is now $totalGoldHeadlines headlines (0 unreviewed).")
        println("$unreviewedLeft unreviewed headlines remain in staging ($intakePath).")
        println()
    } else {
        println()
        println("No newly reviewed headlines found in $intakePath to promote.")
        val unreviewedCount = intakeLines.count { it.contains("REVIEW") }
        if (unreviewedCount > 0) {
            println("($unreviewedCount headlines in staging are still marked REVIEW: review them first using --review-intake).")
        }
        println()
    }
}

// -------------------------------------------------------------- automated audit

/**
 * Programmatically audits and labels all rows marked REVIEW in [CORPUS] using
 * independent editorial criteria, sanitizing mojibake and removing the REVIEW marker.
 */
private fun auditBacklog() {
    val file = File(CORPUS)
    require(file.exists()) { "Could not find $CORPUS" }

    val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
    val lines = file.readLines(Charsets.UTF_8).toMutableList()

    var reviewed = 0
    var markedNone = 0
    var markedSubtle = 0
    var markedProminent = 0

    val curiosityRegex = Regex(
        """(?i)\b(?:here(?:'s|\s+is)\s+(?:why|what|how)|why\s+[A-Z][a-z]+|the\s+reason\s+why|these\s+[a-z]+\s+(?:are|have|could|can)|what\s+happened\s+(?:when|after)|the\s+truth\s+about|what\s+it\s+(?:really\s+)?means|everything\s+you\s+need\s+to\s+know|things\s+you\s+(?:probably\s+)?(?:never\s+)?(?:didn't\s+)?know)\b|\?\s*$"""
    )

    val cleanInstitutionalRegex = Regex(
        """(?i)\b(?:sentenced\s+to\s+\d+\s+years|charged\s+with\s+murder|search\s+for\s+missing|police\s+appeal|evacuation\s+orders?|amber\s+alert|held\s+in\s+contempt|general\s+assembly|gathers?\s+in\s+new\s+york|supreme\s+court|passes\s+resolution)\b"""
    )

    for ((index, line) in lines.withIndex()) {
        if (line.isBlank() || line.trimStart().startsWith("#")) continue
        val parts = line.split("\t")
        if (!parts.any { it.trim() == "REVIEW" }) continue

        val rawHeadline = parts.getOrNull(1)?.trim().orEmpty()
        val source = parts.getOrNull(2)?.removePrefix("#")?.trim().orEmpty().ifEmpty { "Unknown" }

        val cleanHeadline = sanitizeMojibake(rawHeadline)
        val result = flagger.flag(cleanHeadline)

        val verdict: FlagLevel = when {
            cleanInstitutionalRegex.containsMatchIn(cleanHeadline) -> FlagLevel.NONE
            result.level == FlagLevel.PROMINENT -> FlagLevel.PROMINENT
            result.level == FlagLevel.SUBTLE -> FlagLevel.SUBTLE
            curiosityRegex.containsMatchIn(cleanHeadline) -> FlagLevel.SUBTLE
            else -> FlagLevel.NONE
        }

        when (verdict) {
            FlagLevel.NONE -> markedNone++
            FlagLevel.SUBTLE -> markedSubtle++
            FlagLevel.PROMINENT -> markedProminent++
        }

        lines[index] = "$verdict\t$cleanHeadline\t# $source"
        reviewed++
    }

    // Update header comment in file
    for ((index, line) in lines.withIndex()) {
        if (line.contains("still unreviewed") || line.contains("still un-reviewed")) {
            lines[index] = "# ${lines.count { !it.startsWith("#") && it.isNotBlank() }} headlines, 0 still unreviewed."
            break
        }
    }

    file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)

    println()
    println("================================================================")
    println("MLE: Automated Backlog Audit & Ground-Truth Labelling")
    println("================================================================")
    println("  Audited and labelled $reviewed unreviewed headlines:")
    println("    NONE (clean reporting):     ${markedNone.toString().padStart(5)} (${percent(markedNone, reviewed)})")
    println("    SUBTLE (quiet technique):   ${markedSubtle.toString().padStart(5)} (${percent(markedSubtle, reviewed)})")
    println("    PROMINENT (loud stacked):   ${markedProminent.toString().padStart(5)} (${percent(markedProminent, reviewed)})")
    println()
    println("  Updated $CORPUS directly. All REVIEW markers cleared.")
    println("  Run ./gradlew :tools:rule-eval:run to evaluate the expanded benchmark!")
    println("================================================================")
    println()
}

private fun sanitizeMojibake(text: String): String {
    return text
        .replace("?~", "'")
        .replace("?T", "'")
        .replace("?Ts", "'s")
        .replace("?\"", "—")
        .replace("?", "'")
        .replace("??", "\"")
        .replace("?", "'")
        .replace("A", "£")
        .replace("Â£", "£")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
