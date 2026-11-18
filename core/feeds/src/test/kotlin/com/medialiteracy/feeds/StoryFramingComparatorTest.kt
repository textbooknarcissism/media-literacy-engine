package com.medialiteracy.feeds

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import com.medialiteracy.model.PatternHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryFramingComparatorTest {

    @Test
    fun `identifies divergent editorial words in sensational headline compared to calm wire reports`() {
        val reuters = Triple(
            "Fed holds interest rates steady as inflation cools",
            "Reuters",
            FlagResult(FlagLevel.NONE, 0.0, emptyList()),
        )
        val ap = Triple(
            "Federal Reserve leaves key rate unchanged",
            "Associated Press",
            FlagResult(FlagLevel.NONE, 0.0, emptyList()),
        )
        val dailyMail = Triple(
            "BREAKING: Fed's brutal interest rate shock plunges mortgage holders into chaos",
            "Daily Mail",
            FlagResult(
                level = FlagLevel.PROMINENT,
                score = 1.8,
                hits = listOf(
                    PatternHit(Category.FEAR, "brutal", 15..21),
                    PatternHit(Category.FEAR, "plunges into chaos", 43..61),
                ),
            ),
        )

        val report = StoryFramingComparator.compare("story-123", listOf(reuters, ap, dailyMail))

        assertTrue(report.hasFramingContrast)
        assertTrue(report.sharedFactWords.contains("fed") || report.sharedFactWords.contains("rate"))

        val mailComparison = report.comparisons.first { it.sourceName == "Daily Mail" }
        assertFalse(mailComparison.isCalmBaseline)
        assertTrue(mailComparison.divergentEditorialWords.contains("brutal"))
        assertTrue(mailComparison.divergentEditorialWords.contains("plunge") || mailComparison.divergentEditorialWords.contains("chaos"))

        val reutersComparison = report.comparisons.first { it.sourceName == "Reuters" }
        assertTrue(reutersComparison.isCalmBaseline)
        assertTrue(reutersComparison.divergentEditorialWords.isEmpty())
    }

    @Test
    fun `reports no contrast when all reports in cluster are plain`() {
        val bbc = Triple(
            "UK inflation holds at two percent in August",
            "BBC",
            FlagResult(FlagLevel.NONE, 0.0, emptyList()),
        )
        val guardian = Triple(
            "Inflation remains steady at target, ONS figures show",
            "The Guardian",
            FlagResult(FlagLevel.NONE, 0.0, emptyList()),
        )

        val report = StoryFramingComparator.compare("story-456", listOf(bbc, guardian))
        assertFalse(report.hasFramingContrast)
        assertTrue(report.comparisons.all { it.isCalmBaseline })
    }
}
