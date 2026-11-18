package com.medialiteracy.benchmark

enum class SplitPart { TUNE, HELDOUT }

/**
 * Which part of the benchmark a case belongs to, decided once from its id and
 * never re-drawn. Tuning cases are listed individually so rules can be fixed
 * against them; held-out cases are only ever reported as totals.
 */
object Split {
    const val HELDOUT_PERCENT = 30

    fun of(caseId: String): SplitPart {
        val bucket = sha256Hex(caseId).substring(0, 8).toLong(16) % 100
        return if (bucket < HELDOUT_PERCENT) SplitPart.HELDOUT else SplitPart.TUNE
    }
}
