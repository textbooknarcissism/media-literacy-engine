package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.FlagLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadlineScorerTest {
    @Test
    fun `scores levels against the real flagger`() {
        val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
        val dataset = HeadlineDataset("t", "unused", HeadlineFormat.LEGACY, 2026, "project", "Test set")
        val cases = listOf(
            HeadlineCase("a", "Bank of England holds interest rates at 4.25%", FlagLevel.NONE, "BBC News"),
            HeadlineCase("b", "You won't believe what this celebrity did next", FlagLevel.SUBTLE, "Daily Star"),
        )
        val all = HeadlineScorer.score(dataset, cases, flagger).levels(null)
        assertEquals(Rate(1, 1), all.leftAlone)
        assertEquals(Rate(1, 1), all.caught)
    }

    @Test
    fun `scores with context modes`() {
        val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
        val dataset = HeadlineDataset("t", "unused", HeadlineFormat.CONTEXT, 2026, "project", "Test set")
        val cases = listOf(
            HeadlineCase(
                id = "c1",
                headline = "Scientists find cure for memory loss",
                expectedLevel = FlagLevel.NONE,
                outlet = "Science Outlet",
                summary = "The breakthrough in mice shows promising results in early trials.",
            ),
        )
        // With NONE, summary is ignored.
        val noneReport = HeadlineScorer.score(dataset, cases, flagger, ContextMode.NONE)
        assertEquals(1, noneReport.scored.size)

        // With SUMMARY, summary is passed.
        val summaryReport = HeadlineScorer.score(dataset, cases, flagger, ContextMode.SUMMARY)
        assertEquals(1, summaryReport.scored.size)

        // With STORY, story is supported.
        val storyReport = HeadlineScorer.score(dataset, cases, flagger, ContextMode.STORY)
        assertEquals(1, storyReport.scored.size)
    }
}
