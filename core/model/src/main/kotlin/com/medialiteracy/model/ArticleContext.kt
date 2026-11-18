package com.medialiteracy.model

/**
 * Contextual facts about an article known to the caller.
 *
 * Designed to provide context for codebook rule 2: distinguishing opinion,
 * analysis, reviews, and event reporting from headline phrasing alone.
 *
 * Pure, offline, and portable: zero network calls or platform dependencies.
 * MLE never fetches this directly; consuming apps or harvest tools pass it in.
 */
data class ArticleContext(
    /** Explicit article type if already determined. */
    val articleType: ArticleType? = null,
    /** The publication section or beat (e.g. "Opinion", "Culture", "Science"). */
    val pageSection: String? = null,
    /** The web page schema or markup type (e.g. "OpinionNewsArticle", "ReviewNewsArticle"). */
    val pageType: String? = null,
    /** The author byline (e.g. "Jane Smith"). */
    val byline: String? = null,
    /** RSS or article summary description text. */
    val summary: String? = null,
    /** Article body text when available. */
    val storyText: String? = null,
) {
    /**
     * The effective [ArticleType]: the explicit [articleType] first, then the
     * schema [pageType], then a [pageSection] that is a genre label as a whole
     * ("Opinion", "Letters", "Book reviews"). A beat such as "Culture" or
     * "Science" names no type, so straight news there is still judged as news.
     */
    val resolvedArticleType: ArticleType?
        get() = articleType ?: ArticleType.fromPageType(pageType) ?: ArticleType.fromLabel(pageSection)

    companion object {
        val EMPTY = ArticleContext()
    }
}
