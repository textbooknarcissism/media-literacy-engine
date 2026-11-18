package com.medialiteracy.feeds

import com.medialiteracy.model.FlagResult

/**
 * Compares the framings of different outlets covering the exact same event in a story cluster.
 *
 * Rather than lecturing or ranking outlets, this identifies what words were added by
 * sensational headlines that do not appear anywhere in the neutral wire coverage.
 *
 * Pure Kotlin, zero Android dependencies.
 */
object StoryFramingComparator {

    data class HeadlineContrast(
        val headline: String,
        val sourceName: String,
        val flag: FlagResult,
        /** Words this specific outlet injected that were absent in neutral/wire reports. */
        val divergentEditorialWords: List<String>,
        val isCalmBaseline: Boolean,
    )

    data class StoryContrastReport(
        val storyId: String?,
        /** Distinctive factual vocabulary shared across the story cluster. */
        val sharedFactWords: List<String>,
        /** Individual comparisons for each headline in reading order. */
        val comparisons: List<HeadlineContrast>,
        /** True when the cluster contains both calm and flagged framings: ideal for media literacy teaching. */
        val hasFramingContrast: Boolean,
    )

    /**
     * Compares an ordered list of headlines and their flags for a single story cluster.
     *
     * @param items list of (headline, sourceName, flagResult) in feed arrival order
     */
    fun compare(
        storyId: String? = null,
        items: List<Triple<String, String, FlagResult>>,
    ): StoryContrastReport {
        if (items.size < 2) {
            return StoryContrastReport(
                storyId = storyId,
                sharedFactWords = emptyList(),
                comparisons = items.map { (headline, source, flag) ->
                    HeadlineContrast(
                        headline = headline,
                        sourceName = source,
                        flag = flag,
                        divergentEditorialWords = emptyList(),
                        isCalmBaseline = !flag.isFlagged,
                    )
                },
                hasFramingContrast = false,
            )
        }

        val calmItems = items.filter { !it.third.isFlagged }
        val flaggedItems = items.filter { it.third.isFlagged }
        val hasContrast = calmItems.isNotEmpty() && flaggedItems.isNotEmpty()

        // Vocabulary of all calm headlines combined (tokenized, lowercase)
        val calmVocabulary = if (calmItems.isNotEmpty()) {
            calmItems.flatMap { tokenizeWords(it.first) }.toSet()
        } else {
            emptySet()
        }

        // Shared words across headlines in cluster (shared by at least 2 reports, sorted by frequency)
        val sharedWords = items
            .flatMap { tokenizeWords(it.first).distinct() }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val comparisons = items.map { (headline, source, flag) ->
            val headlineWords = tokenizeWords(headline)
            val divergent = if (flag.isFlagged && calmVocabulary.isNotEmpty()) {
                // Find words in this flagged headline that don't appear in calm reports
                val matchedHitTerms = flag.hits
                    .flatMap { tokenizeWords(it.matchedText) }
                    .toSet()

                headlineWords.filter { word ->
                    word !in calmVocabulary && (word in matchedHitTerms || word.length >= 4)
                }.distinct()
            } else {
                emptyList()
            }

            HeadlineContrast(
                headline = headline,
                sourceName = source,
                flag = flag,
                divergentEditorialWords = divergent,
                isCalmBaseline = !flag.isFlagged,
            )
        }

        return StoryContrastReport(
            storyId = storyId,
            sharedFactWords = sharedWords,
            comparisons = comparisons,
            hasFramingContrast = hasContrast,
        )
    }

    private fun tokenizeWords(text: String): List<String> =
        text.split(Regex("""[^A-Za-z0-9]+"""))
            .map { normalizeWord(it) }
            .filter { it.length >= 3 && it !in STOP_WORDS }

    private fun normalizeWord(word: String): String {
        var w = word.lowercase().removeSuffix("'s")
        if (w.length > 4 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") && !w.endsWith("os") && !w.endsWith("as")) {
            w = w.removeSuffix("s")
        }
        return w
    }

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with",
        "about", "against", "between", "into", "through", "during", "before", "after",
        "above", "below", "from", "up", "down", "over", "under", "again", "further",
        "then", "once", "here", "there", "when", "where", "why", "how", "all", "any",
        "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor",
        "not", "only", "own", "same", "so", "than", "too", "very", "can", "will",
        "just", "should", "now", "says", "said"
    )
}
