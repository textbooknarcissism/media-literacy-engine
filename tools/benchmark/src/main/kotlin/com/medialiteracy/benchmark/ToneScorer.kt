package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.model.Category

class ToneTally {
    var tp = 0
    var fp = 0
    var fn = 0
    var tn = 0
    var biasedWordTotal = 0
    var biasedWordCovered = 0
    var toneHitTotal = 0
    var toneHitOnBiasedWord = 0

    val precision: Rate get() = Rate(tp, tp + fp)
    val recall: Rate get() = Rate(tp, tp + fn)
    val wordRecall: Rate get() = Rate(biasedWordCovered, biasedWordTotal)
    val hitPrecision: Rate get() = Rate(toneHitOnBiasedWord, toneHitTotal)
}

/**
 * Proxy check of tone detection on news *sentences* labeled by experts.
 * A sentence counts as toned by the engine when a visible flag includes
 * Alarmist framing, Conflict framing or Opinion as fact.
 */
object ToneScorer {
    private val TONE = setOf(Category.FEAR, Category.RAGE, Category.OPINION)

    fun score(cases: List<SentenceToneCase>, flagger: HeadlineFlagger, hasWordLabels: Boolean): Map<SplitPart, ToneTally> {
        val tallies = SplitPart.entries.associateWith { ToneTally() }
        for (case in cases) {
            val t = tallies.getValue(Split.of(case.id))
            val toneHits = flagger.flag(case.sentence).hits.filter { it.category in TONE }
            val predicted = toneHits.isNotEmpty()
            when {
                predicted && case.toned -> t.tp++
                predicted -> t.fp++
                case.toned -> t.fn++
                else -> t.tn++
            }
            if (hasWordLabels) {
                val wordRanges = case.biasedWords.flatMap { w -> wordRegex(w).findAll(case.sentence).map { it.range }.toList() }
                t.biasedWordTotal += wordRanges.size
                t.biasedWordCovered += wordRanges.count { r -> toneHits.any { it.range.overlaps(r) } }
                t.toneHitTotal += toneHits.size
                t.toneHitOnBiasedWord += toneHits.count { h -> wordRanges.any { it.overlaps(h.range) } }
            }
        }
        return tallies
    }

    /**
     * The same tallies split by the dataset's own lean label for each outlet
     * (BABE marks outlets left, center or right). Research for the lean
     * symmetry audit: if the engine misses one side's loaded words more often,
     * it shows here. MLE itself never knows an outlet's lean.
     */
    fun scoreByGroup(cases: List<SentenceToneCase>, flagger: HeadlineFlagger): Map<String, Map<SplitPart, ToneTally>> =
        cases.filter { it.group != null }.groupBy { it.group!! }.toSortedMap()
            .mapValues { (_, xs) -> score(xs, flagger, hasWordLabels = false) }

    private fun wordRegex(word: String) =
        Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(word.trim()) + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
}
