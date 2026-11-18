package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.model.Category

class TeaseTally {
    var total = 0
    var flagged = 0
    var flaggedCuriosity = 0
    var answerable = 0
    var resolved = 0
    var resolvedCorrect = 0
    var resolvedWrong = 0
}

/**
 * Every Webis-22 post is clickbait, so only catching is measurable here,
 * plus whether the context the resolver attaches is the real answer.
 */
object TeaseScorer {
    const val CORRECT_COVERAGE = 0.6
    const val WRONG_COVERAGE = 0.2

    private val TOKEN = Regex("""[\p{L}\p{N}]+""")
    private val STOP = setOf(
        "the", "and", "for", "that", "with", "this", "from", "are", "was", "were", "has", "have", "had",
        "but", "not", "you", "your", "his", "her", "its", "they", "their", "will", "would", "can", "could",
        "into", "about", "than", "then", "them", "our", "out", "who", "what", "when", "where", "which", "why", "how",
    )

    private fun tokens(text: String): Set<String> =
        TOKEN.findAll(text.lowercase()).map { it.value }.filter { it.length >= 3 && it !in STOP }.toSet()

    /** Share of the spoiler's content words that appear in [text]. */
    fun coverage(text: String, spoiler: String): Double {
        val want = tokens(spoiler)
        if (want.isEmpty()) return 0.0
        val have = tokens(text)
        return want.count { it in have }.toDouble() / want.size
    }

    private fun bestCoverage(text: String, spoilers: List<String>) = spoilers.maxOfOrNull { coverage(text, it) } ?: 0.0

    fun score(cases: List<TeaseCase>, flagger: HeadlineFlagger): Map<SplitPart, TeaseTally> {
        val tallies = SplitPart.entries.associateWith { TeaseTally() }
        for (c in cases) {
            val t = tallies.getValue(Split.of(c.id))
            val result = flagger.flagWithSummary(c.tease, c.summary.ifBlank { null })
            t.total++
            if (result.isFlagged) t.flagged++
            if (Category.CURIOSITY in result.categories) t.flaggedCuriosity++
            if (c.summary.isNotBlank() && bestCoverage(c.summary, c.spoilers) >= CORRECT_COVERAGE) t.answerable++
            val context = result.resolvedContext ?: continue
            t.resolved++
            val cov = bestCoverage(context, c.spoilers)
            if (cov >= CORRECT_COVERAGE) t.resolvedCorrect++ else if (cov < WRONG_COVERAGE) t.resolvedWrong++
        }
        return tallies
    }
}
