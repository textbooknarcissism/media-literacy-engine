package com.medialiteracy.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals

class DelimitedTest {
    @Test
    fun `handles quoted separators, doubled quotes, newlines inside quotes and mid-field apostrophes`() {
        val rows = Delimited.parse("a;b\n\"x;y\";\"he said \"\"hi\"\"\nthen left\"\nplain;Trump's\n", ';')
        assertEquals(
            listOf(listOf("a", "b"), listOf("x;y", "he said \"hi\"\nthen left"), listOf("plain", "Trump's")),
            rows,
        )
    }
}
