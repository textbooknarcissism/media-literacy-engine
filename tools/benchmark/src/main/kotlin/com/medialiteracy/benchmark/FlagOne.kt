package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader

/**
 * Flags the headlines given on the command line and prints what the rules found.
 *
 *   ./gradlew -q :tools:benchmark:run --args="flag 'Scientists discover miracle cure that could end ageing'"
 */
internal fun flagOne(headlines: List<String>): Int {
    if (headlines.isEmpty()) {
        System.err.println("Give at least one headline in quotes.")
        return 2
    }
    val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
    for (headline in headlines) {
        val result = flagger.flag(headline)
        println(headline)
        if (!result.isFlagged) {
            println("  Not flagged.")
        } else {
            println("  ${result.level}")
            for (hit in result.hits) {
                println("  ${hit.category.displayName}: \"${hit.matchedText}\" at ${hit.range.first}..${hit.range.last}")
                println("    Ask yourself: ${hit.askYourself}")
                println("    Rule: ${hit.ruleId}")
            }
        }
        println()
    }
    return 0
}
