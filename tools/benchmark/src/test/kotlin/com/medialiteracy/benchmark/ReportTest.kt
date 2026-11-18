package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import com.medialiteracy.model.PatternHit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTest {
    private val dataset = HeadlineDataset("t", "unused", HeadlineFormat.LEGACY, 2026, "project", "Test set")
    private val flagged = FlagResult(FlagLevel.SUBTLE, 0.5, listOf(PatternHit(Category.FEAR, "alarm", 0..4)))

    @Test
    fun `held-out headlines never appear in the report, tuning mistakes do`() {
        val report = HeadlineReport(dataset, listOf(
            Scored(HeadlineCase("h", "HELDOUT-SECRET headline", FlagLevel.NONE, "o"), flagged, SplitPart.HELDOUT),
            Scored(HeadlineCase("t", "TUNE-VISIBLE headline", FlagLevel.NONE, "o"), flagged, SplitPart.TUNE),
        ))
        val markdown = Report.headlineSection(report)
        assertFalse("HELDOUT-SECRET" in markdown)
        assertTrue("TUNE-VISIBLE" in markdown)
        assertTrue("0/1 (0.0%)" in markdown)
    }
}
