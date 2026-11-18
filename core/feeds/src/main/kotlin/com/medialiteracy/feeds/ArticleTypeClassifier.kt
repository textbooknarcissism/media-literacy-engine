package com.medialiteracy.feeds

import com.medialiteracy.model.ArticleType

/**
 * Works out what kind of piece an article says it is, from the feed item's
 * <category> tags and the section in its link. The result feeds codebook rule 2
 * in the flagger: an opinion piece, editorial, review or letter has told the
 * reader whose judgment it is.
 *
 * Only what the article says about itself counts. The outlet's name never does:
 * "Daily Mail" or "Guardian" decides nothing here, but "/debate/" or
 * "/commentisfree/" in the link does, because that is the outlet labeling the
 * piece.
 *
 * Null when nothing says. Most news items carry no type at all, and an unknown
 * type is treated exactly as before: the headline alone decides.
 */
object ArticleTypeClassifier {

    /**
     * Path segments that label a section, matched as whole segments so that a
     * slug such as "debate-over-tax" or "uk-to-review-defence-spending" never
     * counts. A review slug alone ("...-review-a-bracing-history") is not
     * trusted for the same reason.
     */
    private val OPINION_SEGMENTS = setOf("opinion", "opinions", "commentisfree", "comment", "commentary", "op-ed", "op-eds", "oped", "debate", "columnists", "columns", "voices", "ideas")
    private val EDITORIAL_SEGMENTS = setOf("editorial", "editorials", "leaders")
    private val LETTERS_SEGMENTS = setOf("letters")
    private val REVIEW_SEGMENTS = setOf("review", "reviews")
    private val ANALYSIS_SEGMENTS = setOf("analysis")

    fun classify(link: String?, categories: List<String> = emptyList()): ArticleType? {
        // "tone/comment", "Opinion > Columnists": each part is read on its own.
        categories.firstNotNullOfOrNull(ArticleType::fromLabel)?.let { return it }

        if (link.isNullOrBlank()) return null
        val path = link.substringAfter("://", link).substringAfter('/', "").substringBefore('?').substringBefore('#').lowercase()
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when {
            segments.any { it in EDITORIAL_SEGMENTS } -> ArticleType.EDITORIAL
            segments.any { it in LETTERS_SEGMENTS } -> ArticleType.LETTERS
            segments.any { it in OPINION_SEGMENTS } -> ArticleType.OPINION
            segments.any { it in REVIEW_SEGMENTS } -> ArticleType.REVIEW
            segments.any { it in ANALYSIS_SEGMENTS } -> ArticleType.ANALYSIS
            else -> null
        }
    }
}
