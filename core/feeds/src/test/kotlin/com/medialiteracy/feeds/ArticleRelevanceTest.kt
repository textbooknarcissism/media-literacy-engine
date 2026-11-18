package com.medialiteracy.feeds

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleRelevanceTest {

    @Test
    fun `an article about the headline is accepted`() {
        val headline =
            "Prince Harry reveals he was aware of Earl Spencer's book as King enters war of words"
        val body = """
            Prince Harry has said he was aware of Earl Spencer's forthcoming book before
            its publication, according to a spokesperson. Buckingham Palace declined to
            comment on claims made about King Charles and Diana in the book, which
            Spencer published on Tuesday.
        """.trimIndent()

        assertTrue(ArticleRelevance.matches(headline, body))
    }

    @Test
    fun `the wrong article is rejected`() {
        // The real failure this guard was written for: a fetch returned an
        // unrelated page (e.g. sports match) for a political headline.
        val headline =
            "Prince Harry reveals he was aware of Earl Spencer's book as King enters war of words"
        val body = """
            Wrexham manager Phil Parkinson said his side were second best for long periods
            as they fell to a 3-1 defeat against Queens Park Rangers. Parkinson made five
            changes to the squad that started the previous fixture.
        """.trimIndent()

        assertFalse(ArticleRelevance.matches(headline, body))
    }

    @Test
    fun `a consent wall is rejected`() {
        val headline = "Bank of England holds interest rates at 4.25 percent"
        val body = """
            We and our partners use cookies and similar technologies to store and access
            information on your device. You can accept all cookies, reject non-essential
            cookies, or manage your preferences at any time through the settings page.
        """.trimIndent()

        assertFalse(ArticleRelevance.matches(headline, body))
    }

    @Test
    fun `an empty body is rejected`() {
        assertFalse(ArticleRelevance.matches("Council approves plans for 400 new homes", ""))
    }

    @Test
    fun `a headline too short to judge on is rejected rather than guessed at`() {
        // Falling back to headline-only is weaker; rewriting from the wrong
        // article is wrong. Prefer weaker.
        assertFalse(ArticleRelevance.matches("Man dies", "Any body text at all goes here."))
    }

    @Test
    fun `partial overlap is enough - articles do not repeat the headline verbatim`() {
        val headline = "Swedish PM Kristersson resigns after election defeat"
        val body = """
            Ulf Kristersson has stepped down following his party's performance at the polls,
            telling reporters in Stockholm that he accepted responsibility for the result.
            The Swedish parliament will now begin the process of forming a new government.
        """.trimIndent()

        assertTrue(
            ArticleRelevance.matches(headline, body),
            "a real article paraphrases rather than repeating the headline",
        )
    }
}
