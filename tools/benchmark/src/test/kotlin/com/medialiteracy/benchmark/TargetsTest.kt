package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import com.medialiteracy.model.PatternHit
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetsTest {
    private val flagged = FlagResult(FlagLevel.SUBTLE, 0.5, listOf(PatternHit(Category.FEAR, "alarm", 0..4)))
    private val clean = FlagResult(FlagLevel.NONE, 0.0, emptyList())

    @Test
    fun `wilson interval matches the published values`() {
        val ci = Rate(11, 31).interval()
        assertEquals(0.211, ci.start, 0.001)
        assertEquals(0.531, ci.endInclusive, 0.001)
        assertEquals("11/31 (35.5%) [21–53%]", Rate(11, 31).formatWithInterval())
    }

    @Test
    fun `wilson interval stays inside zero and one at the ends`() {
        val all = Rate(10, 10).interval()
        assertEquals(1.0, all.endInclusive, 1e-9)
        assertTrue(all.start in 0.6..0.75)
        val none = Rate(0, 10).interval()
        assertEquals(0.0, none.start, 1e-9)
        assertEquals("n/a", Rate(0, 0).formatWithInterval())
    }

    @Test
    fun `verdicts separate a real shortfall from noise`() {
        assertEquals(Targets.Verdict.CLEAR, Targets.Result("t", 0.5, Rate(90, 100)).verdict)
        assertEquals(Targets.Verdict.MET, Targets.Result("t", 0.5, Rate(16, 31)).verdict)
        assertEquals(Targets.Verdict.WITHIN, Targets.Result("t", 0.48, Rate(11, 31)).verdict)
        assertEquals(Targets.Verdict.SHORT, Targets.Result("t", 0.52, Rate(139, 310)).verdict)
    }

    @Test
    fun `pooled counts held-out rows of evidence sets only`() {
        fun ds(id: String, tuningOnly: Boolean = false, legacy: Boolean = false) =
            HeadlineDataset(id, "unused", HeadlineFormat.LEGACY, 2026, "project", "Test", tuningOnly = tuningOnly, legacyEvidence = legacy)
        fun rows(ds: HeadlineDataset, part: SplitPart) = HeadlineReport(ds, listOf(
            Scored(HeadlineCase("a", "x", FlagLevel.SUBTLE, "o"), flagged, part),
            Scored(HeadlineCase("b", "y", FlagLevel.SUBTLE, "o"), clean, part),
            Scored(HeadlineCase("c", "z", FlagLevel.NONE, "o"), clean, part),
        ))
        val reports = listOf(
            rows(ds("evidence"), SplitPart.HELDOUT),
            rows(ds("tuning"), SplitPart.TUNE),
            rows(ds("old", legacy = true), SplitPart.HELDOUT),
            rows(ds("rule-material", tuningOnly = true), SplitPart.TUNE),
        )
        val results = Targets.check(reports)
        assertEquals(Rate(1, 1), results.first { it.target == "Pooled held-out left alone" }.measured)
        assertFalse(results.any { "caught" in it.target }, "catch rate is information, never a target")
        assertEquals(Rate(1, 2), Targets.caught(reports))
    }

    @Test
    fun `targets print as plain percents`() {
        assertEquals("97.5%", Targets.percent(Targets.POOLED_LEFT_ALONE))
        assertEquals("96%", Targets.percent(Targets.PER_SET_LEFT_ALONE))
    }
}

class SealedSetTest {
    private val day = LocalDate.of(2026, 11, 16)
    private val sealed = HeadlineDataset(
        "sealed-test", "unused", HeadlineFormat.CONTEXT, 2026, "project", "Test", fresh = true, sealedUntil = day,
    )

    @Test
    fun `sealed until the day, open from it`() {
        assertTrue(sealed.isSealed(day.minusDays(1)))
        assertFalse(sealed.isSealed(day))
        assertFalse(HEADLINE_DATASETS.first().isSealed(day))
    }

    @Test
    fun `every row of a sealed set is held-out, before and after the day`() {
        assertTrue((0 until 200).all { sealed.partOf("case-$it") == SplitPart.HELDOUT })
    }

    @Test
    fun `a sealed set must be fresh evidence`() {
        assertFailsWith<IllegalArgumentException> {
            HeadlineDataset("x", "unused", HeadlineFormat.CONTEXT, 2026, "project", "Test", sealedUntil = day)
        }
        assertFailsWith<IllegalArgumentException> {
            HeadlineDataset("x", "unused", HeadlineFormat.CONTEXT, 2026, "project", "Test", fresh = true, tuningOnly = true, sealedUntil = day)
        }
    }

    @Test
    fun `the sealed section names the set and gives no rate`() {
        val md = Report.sealedSection(sealed, 120)
        assertTrue("Sealed until 2026-11-16" in md)
        assertTrue("120 rows" in md)
        assertFalse("%" in md)
    }

    @Test
    fun `a sealed set is never exported`() {
        val starter = HEADLINE_DATASETS.first { it.id == "starter" }
        val path = File("../..", starter.path).path
        val stillSealed = starter.copy(id = "sealed-starter", path = path, fresh = true, sealedUntil = LocalDate.now().plusDays(30))
        val out = File.createTempFile("cases", ".jsonl").apply { deleteOnExit() }
        val noOwner = File("does-not-exist.tsv")
        assertEquals(0, ExportCases.run(out, noOwner, listOf(stillSealed)))
        val opened = stillSealed.copy(sealedUntil = LocalDate.now())
        assertTrue(ExportCases.run(out, noOwner, listOf(opened)) > 0)
        assertTrue(out.readLines().all { "\"split\":\"HELDOUT\"" in it }, "an unsealed set is held-out only")
    }
}
