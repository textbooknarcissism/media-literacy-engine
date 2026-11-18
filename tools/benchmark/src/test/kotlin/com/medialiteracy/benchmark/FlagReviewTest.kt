package com.medialiteracy.benchmark

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagReviewTest {
    private fun row(id: String, outlet: String) = FlagReview.Row(id, outlet, null, null, "headline $id", null, null, null)
    private fun item(k: String) = FlagReview.Item(k, k, "o", headline = "h", level = "SUBTLE", hits = emptyList())

    @Test
    fun `intake is read by its header, whichever layout`() {
        val file = File(createTempDirectory().toFile(), "intake.tsv")
        file.writeText(
            "# comment\n" +
                "id\tbeat\toutlet\tarticleType\ttypeSource\tfeedUrl\tlink\tharvestedAt\theadline\tsummary\tpageSection\tpageType\tfetchStatus\n" +
                "a1\tus\tABC\tunknown\t\tfeed\thttps://x\t2026-10-20\tThe headline\tThe summary\t\t\tok\n" +
                "a2\tus\tABC\tnews\t\tfeed\t\t2026-10-20\t\t\t\t\tok\n",
        )
        val rows = FlagReview.readIntake(file)
        assertEquals(1, rows.size, "a row with no headline is skipped")
        assertEquals("The headline", rows[0].headline)
        assertEquals("The summary", rows[0].summary)
        assertNull(rows[0].articleType, "unknown means no type")
    }

    @Test
    fun `draw is stable, capped and spread across outlets`() {
        val busy = (1..40).map { row("busy$it", "Busy") to item("busy$it") }
        val quiet = (1..5).map { row("quiet$it", "Quiet") to item("quiet$it") }
        val first = FlagReview.draw(busy + quiet, "2026-10", 10)
        assertEquals(10, first.size)
        assertEquals(5, first.count { it.first.outlet == "Quiet" }, "a busy outlet cannot crowd out a quiet one")
        assertEquals(first.map { it.first.id }, FlagReview.draw((quiet + busy).reversed(), "2026-10", 10).map { it.first.id })
        assertEquals(45, FlagReview.draw(busy + quiet, "2026-10", 50).size, "fewer than the size: all of them")
    }

    @Test
    fun `tally counts only answered questions`() {
        val sample = FlagReview.Sample("2026-10", 10, 31, "intake.tsv", 100, 4, listOf(item("f01"), item("f02"), item("f03"), item("f04")))
        val answers = mapOf(
            "f01" to FlagReview.Answer(clear = true, helps = true),
            "f02" to FlagReview.Answer(clear = false, helps = true),
            "f03" to FlagReview.Answer(clear = true),
            "zz" to FlagReview.Answer(clear = false, helps = false),
        )
        val r = FlagReview.tally(sample, answers)
        assertEquals(Rate(2, 3), r.clear)
        assertEquals(Rate(2, 2), r.helps)
        assertTrue(r.sampled == 4)
    }
}
