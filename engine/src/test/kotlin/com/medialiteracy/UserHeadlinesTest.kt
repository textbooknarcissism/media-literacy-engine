package com.medialiteracy

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import kotlin.test.Test
import kotlin.test.assertFalse

/** Regression checks on headlines read with their summaries: an op-ed and a science question must stay unflagged. */
class UserHeadlinesTest {

    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    @Test
    fun `a signed first-person op-ed is not withheld detail`() {
        val headline = "I have seen with pride how British overseas aid saves childrens' lives. That is why I say cuts should be reversed | David Harewood"
        val summary = "<p>I understand the pressure on the public purse, but I have also seen the effect of our spending on the ground. Our choices can do so much good</p>"
        assertFalse(flagger.flagWithSummary(headline, summary).isFlagged, "David Harewood op-ed should not be flagged as Withheld detail")
    }

    @Test
    fun `a plain science question is not withheld detail`() {
        val headline = "Can Species Evolve Fast Enough to Survive Climate Change?"
        val summary = "<p>Scientists study how quickly species can adapt to rising global temperatures and shifting weather patterns.</p>"
        assertFalse(flagger.flagWithSummary(headline, summary).isFlagged, "Science question should not be flagged as Withheld detail")
    }
}
