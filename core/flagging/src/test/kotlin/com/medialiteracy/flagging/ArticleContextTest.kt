package com.medialiteracy.flagging

import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleContextTest {

    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    @Test
    fun `resolvedArticleType respects explicit articleType first`() {
        val ctx = ArticleContext(
            articleType = ArticleType.NEWS,
            pageSection = "Opinion",
            pageType = "OpinionNewsArticle",
        )
        assertEquals(ArticleType.NEWS, ctx.resolvedArticleType)
    }

    @Test
    fun `resolvedArticleType infers type from pageType schema markup`() {
        assertEquals(
            ArticleType.OPINION,
            ArticleContext(pageType = "OpinionNewsArticle").resolvedArticleType,
        )
        assertEquals(
            ArticleType.REVIEW,
            ArticleContext(pageType = "ReviewNewsArticle").resolvedArticleType,
        )
        assertEquals(
            ArticleType.ANALYSIS,
            ArticleContext(pageType = "AnalysisNewsArticle").resolvedArticleType,
        )
    }

    @Test
    fun `resolvedArticleType infers type from section labels`() {
        assertEquals(
            ArticleType.OPINION,
            ArticleContext(pageSection = "Opinion").resolvedArticleType,
        )
        assertEquals(
            ArticleType.OPINION,
            ArticleContext(pageSection = "Comment is free").resolvedArticleType,
        )
        assertEquals(
            ArticleType.OPINION,
            ArticleContext(pageSection = "Opinion > Columnists").resolvedArticleType,
        )
        assertEquals(
            ArticleType.EDITORIAL,
            ArticleContext(pageSection = "The Guardian view").resolvedArticleType,
        )
        assertEquals(
            ArticleType.REVIEW,
            ArticleContext(pageSection = "Book reviews").resolvedArticleType,
        )
        assertEquals(
            ArticleType.ANALYSIS,
            ArticleContext(pageSection = "News Analysis").resolvedArticleType,
        )
        assertEquals(
            ArticleType.LETTERS,
            ArticleContext(pageSection = "Letters").resolvedArticleType,
        )
        assertNull(
            ArticleContext(pageSection = "World News").resolvedArticleType,
        )
        assertEquals(
            ArticleType.OPINION,
            ArticleContext(pageType = "OpinionNewsArticle", pageSection = "Politics").resolvedArticleType,
        )
        assertNull(
            ArticleContext.EMPTY.resolvedArticleType,
        )
    }

    @Test
    fun `headline flagger suppresses opinion-as-fact for opinion and review sections`() {
        val thesis = "The prime minister's speech was tone-deaf and laughable"

        // Straight news / unlabeled: outlet is stating an evaluative thesis as headline fact
        val unlabelled = flagger.flag(thesis)
        assertTrue(Category.OPINION in unlabelled.categories)

        // With opinion section: codebook rule 2 applies, signed view is permitted
        val opinionCtx = ArticleContext(pageSection = "Opinion")
        val opinionResult = flagger.flag(thesis, opinionCtx)
        assertFalse(Category.OPINION in opinionResult.categories)

        // With schema pageType: also suppressed
        val schemaCtx = ArticleContext(pageType = "OpinionNewsArticle")
        val schemaResult = flagger.flag(thesis, schemaCtx)
        assertFalse(Category.OPINION in schemaResult.categories)

        // With a reviews section: also suppressed
        val reviewCtx = ArticleContext(pageSection = "Reviews")
        val reviewResult = flagger.flag(thesis, reviewCtx)
        assertFalse(Category.OPINION in reviewResult.categories)

        // Straight news section: remains flagged
        val newsCtx = ArticleContext(pageSection = "Politics")
        val newsResult = flagger.flag(thesis, newsCtx)
        assertTrue(Category.OPINION in newsResult.categories)
    }

    @Test
    fun `a beat that only contains a genre word is not a signed view`() {
        for (section in listOf("Culture", "Culture & Arts", "Agriculture", "Newsletters", "Opinion polls", "Film", "Features")) {
            assertNull(ArticleContext(pageSection = section).resolvedArticleType, section)
        }
        // News in a culture section is still judged as news.
        val thesis = "The prime minister's speech was tone-deaf and laughable"
        assertTrue(Category.OPINION in flagger.flag(thesis, ArticleContext(pageSection = "Culture")).categories)
    }

    private val labClaim = "New drug reverses Alzheimer's memory loss"
    private val labCaveat = "The drug restored memory in mice, researchers said on Tuesday."
    private val filler = "Weather was mild across the region today and little else happened. ".repeat(80)

    @Test
    fun `story text is read after the summary`() {
        val ctx = ArticleContext(
            summary = "Officials gave no further detail about the timing of any announcement.",
            storyText = labCaveat,
        )
        val result = flagger.flag(labClaim, ctx)
        assertTrue(Category.OVERHYPE in result.categories)
        assertEquals(labCaveat, result.resolvedContext)
    }

    @Test
    fun `only the opening of the story is read`() {
        val far = flagger.flag(labClaim, ArticleContext(storyText = filler + labCaveat))
        assertEquals(flagger.flag(labClaim).categories, far.categories)
        assertNull(far.resolvedContext)

        val near = flagger.flag(labClaim, ArticleContext(storyText = labCaveat + " " + filler))
        assertTrue(Category.OVERHYPE in near.categories)
    }

    @Test
    fun `teases and curiosity gaps are still flagged in opinion sections`() {
        val tease = "Here's why the pound is falling"
        val ctx = ArticleContext(pageSection = "Opinion")
        val result = flagger.flag(tease, ctx)
        assertTrue(Category.CURIOSITY in result.categories)
    }

    @Test
    fun `summary in article context resolves curiosity gaps and attaches explanation`() {
        val tease = "Doctor reveals the 1 common breakfast food you should never eat"
        val ctx = ArticleContext(
            summary = "Dr. Jane Evans warned on Monday that sweetened instant oatmeal causes severe glucose spikes.",
        )
        val result = flagger.flag(tease, ctx)
        assertTrue(result.resolvedContext != null)
        assertTrue(result.resolvedContext!!.contains("sweetened instant oatmeal"))
    }

    @Test
    fun `empty article context matches standard flag call exactly`() {
        val headline = "Sources say rates could fall in the autumn"
        val baseline = flagger.flag(headline)
        val withEmptyCtx = flagger.flag(headline, ArticleContext.EMPTY)

        assertEquals(baseline.level, withEmptyCtx.level)
        assertEquals(baseline.score, withEmptyCtx.score)
        assertEquals(baseline.categories, withEmptyCtx.categories)
    }
}
