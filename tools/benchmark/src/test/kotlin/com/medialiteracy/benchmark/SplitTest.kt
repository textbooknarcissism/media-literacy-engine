package com.medialiteracy.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitTest {
    @Test
    fun `the same id always lands in the same part`() {
        assertEquals(Split.of("abc"), Split.of("abc"))
    }

    @Test
    fun `about thirty percent of ids are held out`() {
        val held = (0 until 10_000).count { Split.of("case-$it") == SplitPart.HELDOUT }
        assertTrue(held in 2_700..3_300, "held-out share was $held of 10000")
    }
}
