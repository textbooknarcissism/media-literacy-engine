package com.medialiteracy.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals

class TeaseTest {
    @Test
    fun `parses a Webis-22 line`() {
        val case = TeaseSources.parseWebisLine(
            """{"uuid":"u1","postText":["Sequel Delayed for Weird Reason"],"targetDescription":"Creator teases a delay.","spoiler":["the plot is too disturbing"],"tags":["passage"]}""",
        )
        assertEquals("u1", case.id)
        assertEquals("Sequel Delayed for Weird Reason", case.tease)
        assertEquals("Creator teases a delay.", case.summary)
        assertEquals(listOf("the plot is too disturbing"), case.spoilers)
    }

    @Test
    fun `a missing description becomes an empty summary`() {
        val case = TeaseSources.parseWebisLine("""{"uuid":"u2","postText":["Tease"],"targetDescription":null,"spoiler":["answer"]}""")
        assertEquals("", case.summary)
    }

    @Test
    fun `coverage counts content words only`() {
        assertEquals(1.0, TeaseScorer.coverage("Cawthon said the plot is too disturbing to release", "the plot is too disturbing"))
        assertEquals(0.0, TeaseScorer.coverage("Nothing relevant here", "plot disturbing"))
    }
}
