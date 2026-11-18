package com.medialiteracy.feeds

/**
 * Checks that fetched article text is actually about the headline it was
 * fetched for.
 *
 * This exists to protect reader trust when cross-referencing text. When
 * a fetch returns something other than the intended article (a consent wall, a
 * paywall, an error page, a section index, or simply the wrong page) the
 * extractor still returns perfectly good-looking prose. If context resolution
 * uses that text, the reader sees details about a completely different story.
 *
 * Seen in testing: a fetch for Prince Harry and Earl Spencer returned an
 * article about "Wrexham manager Phil Parkinson citing squad changes after 3-1
 * defeat to QPR". Nothing in the pipeline objected, because nothing in the
 * pipeline was checking.
 *
 * The test is deliberately loose. A headline and its article always share
 * proper nouns and subject words; an unrelated page shares almost none. This
 * only has to catch the catastrophic case, not judge quality.
 */
object ArticleRelevance {

    /**
     * @return true if [body] plausibly belongs to [headline].
     */
    fun matches(headline: String, body: String, minimumOverlap: Double = 0.25): Boolean {
        val headlineWords = contentWords(headline)
        // Too little to judge on. Assume the worst and fall back to
        // headline-only, which is weaker but never wrong about the subject.
        if (headlineWords.size < 3) return false
        if (body.isBlank()) return false

        val bodyWords = contentWords(body).toHashSet()
        val shared = headlineWords.count { it in bodyWords }

        return shared.toDouble() / headlineWords.size >= minimumOverlap
    }

    private fun contentWords(text: String): List<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 && it !in STOP_WORDS }
        .distinct()

    /**
     * Words long enough to survive the length filter but carrying no subject
     * information, so they'd inflate the overlap between any two texts.
     *
     * Shared with [StoryGrouper] rather than copied. Both are asking "are these
     * two pieces of text about the same thing", and two separate lists would
     * quietly become two different answers to that question.
     */
    internal val STOP_WORDS = setOf(
        "after", "about", "again", "against", "amid", "been", "before", "being",
        "between", "both", "could", "does", "doing", "down", "during", "each",
        "have", "having", "here", "how", "into",
        "from", "further", "itself", "just", "more", "most", "only", "other", "over", "own",
        "same", "should", "some", "such", "than", "that", "their", "them",
        "then", "there", "these", "they", "this", "those", "through", "under",
        "until", "very", "were", "what", "when", "where", "which", "while",
        "who", "whom", "why", "will", "with", "would", "your", "says", "said",
        "told", "news", "also", "make", "made", "back", "even", "much", "many",
    )
}
