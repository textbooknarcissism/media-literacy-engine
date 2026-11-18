package com.medialiteracy.adversarial

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel

/**
 * Offline adversarial stress-testing harness for MLE detection rules.
 *
 * Run it with:
 *   ./gradlew :tools:adversarial-fuzz:run
 *   ./gradlew :tools:adversarial-fuzz:run --args="--traps"
 *   ./gradlew :tools:adversarial-fuzz:run --args="--evasions"
 */
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "--all"

    val pack = RulePackLoader.loadDefault()
    val flagger = HeadlineFlagger(pack)

    val allCases = AdversarialBattery.BUILT_IN_CASES
    val casesToRun = when (mode) {
        "--traps" -> allCases.filter { it.challengeType == ChallengeType.FALSE_POSITIVE_TRAP }
        "--evasions" -> allCases.filter { it.challengeType == ChallengeType.EVASION_ATTACK }
        else -> allCases
    }

    println()
    println("================================================================")
    println("  🥊 Gauntlet: Trick Headline & Evasion Test")
    println("  Testing engine defenses against deceptive test cases")
    println("================================================================")
    println()

    var trapCount = 0
    var trapPassed = 0
    var evasionCount = 0
    var evasionPassed = 0

    val failures = mutableListOf<FailureReport>()

    for (c in casesToRun) {
        val result = flagger.flag(c.headline)
        val passed = when (c.challengeType) {
            ChallengeType.FALSE_POSITIVE_TRAP -> {
                trapCount++
                val isClean = result.level == FlagLevel.NONE
                if (isClean) trapPassed++
                isClean
            }
            ChallengeType.EVASION_ATTACK -> {
                evasionCount++
                val isCaught = result.level != FlagLevel.NONE
                if (isCaught) evasionPassed++
                isCaught
            }
        }

        if (!passed) {
            failures += FailureReport(c, result.level, result.score, result.hits.joinToString(", ") { "${it.category.displayName} -> \"${it.matchedText}\"" })
        }
    }

    // Results summary
    println("  TOTAL TEST CASES RUN: ${casesToRun.size}")
    println()

    if (trapCount > 0) {
        val trapPercent = (trapPassed * 100.0 / trapCount).toInt()
        println("  Honest News Protection (False Alarm Safety):")
        println("    $trapPassed / $trapCount ($trapPercent%) correctly left alone")
        if (trapPercent == 100) {
            println("    ✓ Zero false alarms triggered on deceptive straight news headlines.")
        } else {
            println("    ✗ FAILED: False alarms were triggered on honest headlines!")
        }
    }

    if (evasionCount > 0) {
        val evasionPercent = (evasionPassed * 100.0 / evasionCount).toInt()
        println("  Sneaky Clickbait Detection (Catch Rate):")
        println("    $evasionPassed / $evasionCount ($evasionPercent%) successfully caught")
        if (evasionPercent == 100) {
            println("    ✓ All tricky evasion headlines were successfully caught.")
        } else {
            println("    ✗ FAILED: Some tricky headlines slipped past the rules!")
        }
    }

    println()

    val totalPassed = trapPassed + evasionPassed
    val totalTested = trapCount + evasionCount
    val overallScore = if (totalTested > 0) (totalPassed * 100.0 / totalTested).toInt() else 0
    println("  OVERALL DEFENSE SCORE: $overallScore% ($totalPassed / $totalTested)")
    println("================================================================")
    println()

    if (failures.isNotEmpty()) {
        println("HEADLINES THAT FAILED:")
        println("----------------------")
        for (f in failures) {
            val typeStr = if (f.case.challengeType == ChallengeType.FALSE_POSITIVE_TRAP) "FALSE ALARM (Flagged an honest headline)" else "MISSED (Trick headline slipped through)"
            println("  [$typeStr]")
            println("  Headline: \"${f.case.headline}\"")
            println("  Target Category: ${f.case.targetCategory?.displayName ?: "None"} | Level: ${f.actualLevel}")
            println("  Why it matters: ${f.case.rationale}")
            if (f.triggeredHits.isNotEmpty()) {
                println("  Triggered By: ${f.triggeredHits}")
            }
            println()
        }
        kotlin.system.exitProcess(1)
    } else {
        println("  ✓ PASSED: The engine was not tricked by any test cases!")
        println()
    }
}

private data class FailureReport(
    val case: AdversarialCase,
    val actualLevel: FlagLevel,
    val score: Double,
    val triggeredHits: String,
)
