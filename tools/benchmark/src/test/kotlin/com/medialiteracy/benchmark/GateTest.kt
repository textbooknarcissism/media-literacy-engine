package com.medialiteracy.benchmark

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GateTest {
    private val base = Baseline(mapOf("harvested" to DatasetBaseline(Rate(100, 100), Rate(50, 50), Rate(40, 50))))

    @Test
    fun `unchanged numbers pass`() {
        assertTrue(Gate.check(base, base.datasets).isEmpty())
    }

    @Test
    fun `any drop in left alone correctly fails, tuning or held-out`() {
        val now = mapOf("harvested" to DatasetBaseline(Rate(99, 100), Rate(50, 50), Rate(40, 50)))
        assertEquals(1, Gate.check(base, now).size)
    }

    @Test
    fun `recall may drop by one point but not more`() {
        val small = mapOf("harvested" to DatasetBaseline(Rate(100, 100), Rate(50, 50), Rate(395, 500)))
        assertTrue(Gate.check(base, small).isEmpty(), "0.80 to 0.79 is within one point")
        val large = mapOf("harvested" to DatasetBaseline(Rate(100, 100), Rate(50, 50), Rate(39, 50)))
        assertEquals(1, Gate.check(base, large).size, "0.80 to 0.78 is two points")
    }

    @Test
    fun `a dataset missing from the run fails`() {
        assertEquals(1, Gate.check(base, emptyMap()).size)
    }

    @Test
    fun `baseline round-trips and a missing file reads as null`() {
        val file = File.createTempFile("baseline", ".json").apply { deleteOnExit() }
        Baseline.write(file, base.copy(knownTerms = listOf("slams")))
        assertEquals(base.copy(knownTerms = listOf("slams")), Baseline.read(file))
        assertNull(Baseline.readOrNull(File(file.parentFile, "does-not-exist-${System.nanoTime()}.json")))
    }
}
