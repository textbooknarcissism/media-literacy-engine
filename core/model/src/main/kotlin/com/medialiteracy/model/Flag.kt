package com.medialiteracy.model

/**
 * How strongly, if at all, a headline should be marked in the feed.
 *
 * The three levels exist because of the single biggest risk in this whole app:
 * over-flagging. Words like "could" and "may" are completely normal in honest
 * science, weather and markets reporting. If every one of those got a loud
 * marker, users would stop trusting the marker within a day and the feature
 * would be worthless. So a lone soft signal stays invisible, and the flag only
 * becomes prominent when several techniques stack up in one headline.
 */
enum class FlagLevel {
    /** Nothing shown. The headline is left completely alone. */
    NONE,

    /** A quiet marker indicating a mild or single technique detection. */
    SUBTLE,

    /** The same marker, shown at full strength. Several techniques stacked. */
    PROMINENT,
}

/** One specific thing the detector matched inside a headline. */
data class PatternHit(
    val category: Category,
    /** The exact text that matched, as it appeared in the headline. */
    val matchedText: String,
    /** Where it was found, so a UI can underline it if it needs to. */
    val range: IntRange,
    /**
     * Which rule fired: "term:slams", "signal:staged_exchange",
     * "compound:science_overhype", or "summary:..." for a flag the feed summary
     * confirmed. Lets an inspection view name the rule and a "was this flagged
     * correctly?" answer point at it. Null for hits built elsewhere.
     */
    val ruleId: String? = null,
    /** What this hit added to the score: 1.0 full strength, 0.5 notice, below that soft. */
    val weight: Double? = null,
    /**
     * A question written for the rule that fired ("Who called it a nightmare?"),
     * or null when the rule has none. Show [askYourself] rather than this, so
     * a rule without its own question falls back to the technique's.
     */
    val question: String? = null,
) {
    /** The question to put to the reader: this rule's own, else the technique's. */
    val askYourself: String get() = question ?: category.askYourself
}

/**
 * The full result of checking one headline.
 *
 * [score] is the raw number the thresholds are applied to. It is kept around
 * because the rule-tuning tool needs it: in consuming applications, only [level] and
 * [categories] are ever shown.
 */
data class FlagResult(
    val level: FlagLevel,
    val score: Double,
    val hits: List<PatternHit>,
    /**
     * When the article's summary provides the fact or explanation that the headline
     * deliberately held back (curiosity gap), this carries that clean resolving sentence.
     */
    val resolvedContext: String? = null,
) {
    /** The distinct techniques found, in the order they were detected. */
    val categories: List<Category> get() = hits.map { it.category }.distinct()

    val isFlagged: Boolean get() = level != FlagLevel.NONE

    companion object {
        val NOT_FLAGGED = FlagResult(FlagLevel.NONE, 0.0, emptyList(), null)
    }
}

/**
 * The family to show an icon for.
 *
 * Hits arrive strongest-first, so the first one is the technique doing the most
 * work in the headline, which is the one worth naming with a symbol.
 */
val FlagResult.family: Family?
    get() = hits.firstOrNull()?.let { Family.of(it.category) }
