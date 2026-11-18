package com.medialiteracy.benchmark

import java.util.Locale

/**
 * The left-alone targets (docs/teaching-reset-2026-10.md §6): honest headlines
 * MLE must leave alone, checked against held-out results with their 95%
 * intervals, so a reader can tell a real shortfall from a few headlines'
 * worth of noise. MLE is a teaching tool, so a wrong flag is what costs; the
 * catch rate is reported by [caught] as information and never has a target.
 *
 * Pooled means the held-out rows of every set that is evidence: not legacy,
 * not tuning-only, not sealed.
 */
object Targets {
    const val POOLED_LEFT_ALONE = 0.975
    const val PER_SET_LEFT_ALONE = 0.96

    enum class Verdict(val text: String) {
        CLEAR("met, even at the interval's low end"),
        MET("met, but within noise"),
        WITHIN("short, but within noise"),
        SHORT("short, even at the interval's high end"),
    }

    data class Result(val target: String, val goal: Double, val measured: Rate, val note: String = "") {
        val verdict: Verdict
            get() {
                val ci = measured.interval()
                return when {
                    ci.start >= goal -> Verdict.CLEAR
                    measured.value >= goal -> Verdict.MET
                    ci.endInclusive >= goal -> Verdict.WITHIN
                    else -> Verdict.SHORT
                }
            }
    }

    fun check(reports: List<HeadlineReport>): List<Result> {
        val evidence = evidence(reports)
        return buildList {
            add(Result("Pooled held-out left alone", POOLED_LEFT_ALONE, pooled(evidence).leftAlone))
            evidence.map { it to it.levels(SplitPart.HELDOUT).leftAlone }
                .filter { it.second.total > 0 }
                .minByOrNull { it.second.value }
                ?.let { (r, rate) -> add(Result("Held-out left alone, every set", PER_SET_LEFT_ALONE, rate, "lowest: ${r.dataset.id}")) }
        }
    }

    /** Pooled held-out catch rate: information, not a goal. */
    fun caught(reports: List<HeadlineReport>): Rate = pooled(evidence(reports)).caught

    private fun evidence(reports: List<HeadlineReport>) = reports.filter { !it.dataset.legacyEvidence && !it.dataset.tuningOnly }

    private fun pooled(evidence: List<HeadlineReport>) = LevelTally().also { t ->
        evidence.flatMap { r -> r.scored.filter { it.part == SplitPart.HELDOUT } }
            .forEach { t.add(it.case.expectedLevel, it.got.level) }
    }

    fun percent(goal: Double): String = String.format(Locale.ROOT, "%.1f", goal * 100).removeSuffix(".0") + "%"
}
