package com.medialiteracy.feeds

import com.medialiteracy.model.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleTypeClassifierTest {

    @Test
    fun `reads the type from the item's category tags`() {
        assertEquals(ArticleType.OPINION, ArticleTypeClassifier.classify(null, listOf("Opinion")))
        assertEquals(ArticleType.OPINION, ArticleTypeClassifier.classify(null, listOf("Comment is free")))
        assertEquals(ArticleType.EDITORIAL, ArticleTypeClassifier.classify(null, listOf("tone/editorials")))
        assertEquals(ArticleType.LETTERS, ArticleTypeClassifier.classify(null, listOf("Letters")))
        assertEquals(ArticleType.REVIEW, ArticleTypeClassifier.classify(null, listOf("Film reviews")))
        assertEquals(ArticleType.ANALYSIS, ArticleTypeClassifier.classify(null, listOf("Analysis")))
        assertNull(ArticleTypeClassifier.classify(null, listOf("Politics", "UK news")))
    }

    @Test
    fun `reads the type from a section in the link`() {
        assertEquals(ArticleType.OPINION, ArticleTypeClassifier.classify("https://www.dailymail.co.uk/debate/article-16160149/column.html"))
        assertEquals(ArticleType.OPINION, ArticleTypeClassifier.classify("https://www.theguardian.com/commentisfree/2026/oct/01/ai-is-not-a-doctor"))
        assertEquals(ArticleType.OPINION, ArticleTypeClassifier.classify("https://www.nytimes.com/2026/10/01/opinion/ai-doctor.html"))
        assertEquals(ArticleType.OPINION, ArticleTypeClassifier.classify("https://www.washingtonpost.com/opinions/2026/10/01/texas-dreamers/"))
        assertEquals(ArticleType.EDITORIAL, ArticleTypeClassifier.classify("https://example.com/editorials/2026/the-city-budget"))
        assertEquals(ArticleType.REVIEW, ArticleTypeClassifier.classify("https://www.example.com/games/reviews/star-wars-outlaws"))
    }

    @Test
    fun `news and slugs that merely contain a section word are left unknown`() {
        assertNull(ArticleTypeClassifier.classify("https://www.washingtonpost.com/world/2026/09/29/us-withdraws-forces-iraq-balance-power-shifts-iran/"))
        assertNull(ArticleTypeClassifier.classify("https://www.bbc.co.uk/news/uk-to-review-defence-spending"))
        assertNull(ArticleTypeClassifier.classify("https://www.example.com/news/debate-over-tax-rises"))
        assertNull(ArticleTypeClassifier.classify("https://www.theguardian.com/books/2026/sep/29/the-fall-of-the-west-by-owen-jones-review-a-bracing-history"))
        assertNull(ArticleTypeClassifier.classify(null))
    }
}
