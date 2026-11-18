package com.medialiteracy.model

/**
 * What kind of piece an article says it is: the section feed it came from, a
 * category tag on the feed item, or a section in its link.
 *
 * This is what the article says about itself, never a judgment about the
 * outlet. Codebook rule 2: an opinion piece, an editorial, a review or a letter
 * has already told the reader whose judgment it is, so it is never opinion
 * presented as fact. Its other techniques still count.
 */
enum class ArticleType(val id: String) {
    NEWS("news"),
    OPINION("opinion"),
    EDITORIAL("editorial"),
    ANALYSIS("analysis"),
    FEATURE("feature"),
    REVIEW("review"),
    LETTERS("letters"),
    ;

    /** True when the reader has been told the piece is somebody's view (codebook rule 2). */
    val isSignedView: Boolean
        get() = this == OPINION || this == EDITORIAL || this == REVIEW || this == LETTERS

    companion object {
        /** The type with this id ("opinion", "review", ...), or null for anything else, including "unknown". */
        fun fromId(id: String?): ArticleType? {
            val key = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == key }
        }

        private val OPINION_LABEL = Regex(
            """^(?:opinion|opinions|comment|comment\s+is\s+free|commentary|op-?eds?|columns?|columnists?|perspectives?|guest\s+essays?|voices)$""",
            RegexOption.IGNORE_CASE,
        )
        private val EDITORIAL_LABEL = Regex("""^(?:editorials?|leaders?|our\s+view|the\s+[\p{L}' ]{1,40}\s+view)$""", RegexOption.IGNORE_CASE)
        private val LETTERS_LABEL = Regex("""^(?:letters?|letters\s+to\s+the\s+editor|readers'?\s+letters)$""", RegexOption.IGNORE_CASE)
        private val REVIEW_LABEL = Regex("""^(?:reviews?|[\p{L} ]{1,30}\s+reviews?)$""", RegexOption.IGNORE_CASE)
        private val ANALYSIS_LABEL = Regex("""^(?:analysis|news\s+analysis|explainers?)$""", RegexOption.IGNORE_CASE)

        /**
         * The type a section or category label names: "Opinion", "Letters",
         * "Book reviews", "tone/comment", "Opinion > Columnists". Each part must
         * be the whole label, so a beat that merely contains a genre word
         * ("Culture", "Agriculture", "Newsletters", "Opinion polls") names no
         * type. A beat is where a piece sits, not whose judgment it is.
         */
        fun fromLabel(label: String?): ArticleType? {
            if (label.isNullOrBlank()) return null
            for (part in label.split('/', '>', '|').map { it.trim() }.filter { it.isNotEmpty() }) {
                when {
                    EDITORIAL_LABEL.matches(part) -> return EDITORIAL
                    LETTERS_LABEL.matches(part) -> return LETTERS
                    OPINION_LABEL.matches(part) -> return OPINION
                    REVIEW_LABEL.matches(part) -> return REVIEW
                    ANALYSIS_LABEL.matches(part) -> return ANALYSIS
                }
            }
            return null
        }

        /**
         * The type a schema.org page type names (`OpinionNewsArticle`,
         * `ReviewNewsArticle`, `AnalysisNewsArticle`, `Review`). Plain
         * `NewsArticle` or `Article` says nothing about whose judgment it is.
         */
        fun fromPageType(pageType: String?): ArticleType? {
            val type = pageType?.trim() ?: return null
            return when {
                type.contains("Opinion", ignoreCase = true) -> OPINION
                type.contains("Editorial", ignoreCase = true) -> EDITORIAL
                type.contains("Review", ignoreCase = true) -> REVIEW
                type.contains("Analysis", ignoreCase = true) -> ANALYSIS
                else -> null
            }
        }
    }
}
