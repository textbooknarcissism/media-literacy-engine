package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class Rate(val correct: Int, val total: Int) {
    val value: Double get() = if (total == 0) 1.0 else correct.toDouble() / total
    fun format(): String =
        if (total == 0) "n/a" else String.format(Locale.ROOT, "%d/%d (%.1f%%)", correct, total, 100.0 * value)

    /**
     * The 95% Wilson score interval: the range the true rate plausibly lies in,
     * given how few headlines were counted. Wilson rather than the textbook
     * p ± 1.96·se because most of our sets are small and many rates sit near
     * 0% or 100%, where the textbook interval runs past the ends.
     */
    fun interval(): ClosedFloatingPointRange<Double> {
        if (total == 0) return 0.0..1.0
        val n = total.toDouble()
        val p = correct / n
        val z2 = Z95 * Z95
        val centre = (p + z2 / (2 * n)) / (1 + z2 / n)
        val half = Z95 * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / (1 + z2 / n)
        return (centre - half).coerceAtLeast(0.0)..(centre + half).coerceAtMost(1.0)
    }

    /** [format] plus the 95% interval in whole percents: "11/31 (35.5%) [21–53%]". */
    fun formatWithInterval(): String {
        if (total == 0) return "n/a"
        val ci = interval()
        return String.format(Locale.ROOT, "%s [%.0f–%.0f%%]", format(), 100 * ci.start, 100 * ci.endInclusive)
    }

    private companion object {
        const val Z95 = 1.959964
    }
}

/** "Left alone correctly" and "caught when it should", the two numbers the project has always led with. */
class LevelTally {
    private var cleanTotal = 0
    private var cleanCorrect = 0
    private var baitTotal = 0
    private var baitCaught = 0

    fun add(expected: FlagLevel, got: FlagLevel) {
        if (expected == FlagLevel.NONE) {
            cleanTotal++
            if (got == FlagLevel.NONE) cleanCorrect++
        } else {
            baitTotal++
            if (got != FlagLevel.NONE) baitCaught++
        }
    }

    val leftAlone: Rate get() = Rate(cleanCorrect, cleanTotal)
    val caught: Rate get() = Rate(baitCaught, baitTotal)
}

/** Per-technique counts. A detector hit counts as correct only if the label lists that technique. */
class TechniqueTally {
    private val tp = mutableMapOf<Category, Int>()
    private val fp = mutableMapOf<Category, Int>()
    private val fn = mutableMapOf<Category, Int>()

    fun add(expected: Set<Category>, got: Set<Category>) {
        for (c in got) if (c in expected) tp.merge(c, 1, Int::plus) else fp.merge(c, 1, Int::plus)
        for (c in expected) if (c !in got) fn.merge(c, 1, Int::plus)
    }

    fun precision(c: Category): Rate = Rate(tp[c] ?: 0, (tp[c] ?: 0) + (fp[c] ?: 0))
    fun recall(c: Category): Rate = Rate(tp[c] ?: 0, (tp[c] ?: 0) + (fn[c] ?: 0))
}
