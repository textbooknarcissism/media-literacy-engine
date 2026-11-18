package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadlineFilesTest {
    private fun temp(text: String) = File.createTempFile("bench", ".tsv").apply { deleteOnExit(); writeText(text) }

    @Test
    fun `legacy reader skips comments and REVIEW rows and reads the outlet`() {
        val cases = HeadlineFiles.readLegacy(temp(
            "# comment\n" +
                "NONE\tCouncil approves budget\t# BBC News\n" +
                "SUBTLE\tYou won't believe this\t# Daily Star\tREVIEW\n" +
                "PROMINENT\tHorror as storm hits\n",
        ))
        assertEquals(2, cases.size)
        assertEquals("BBC News", cases[0].outlet)
        assertEquals("unknown", cases[1].outlet)
        assertNull(cases[0].expectedTechniques)
    }

    @Test
    fun `broadsheet reader parses techniques, metadata and an empty summary`() {
        val labelled = listOf("SUBTLE", "opinion_as_fact,manufactured_fear", "id1", "The Guardian", "news", "publisher-tag", "https://x", "Headline here", "Summary here")
        val plain = listOf("NONE", "-", "id2", "BBC News", "news", "section-feed", "https://y", "Plain headline", "")
        val cases = HeadlineFiles.readBroadsheet(temp("# header\n${labelled.joinToString("\t")}\n${plain.joinToString("\t")}\n"))

        assertEquals(setOf(Category.OPINION, Category.FEAR), cases[0].expectedTechniques)
        assertEquals("publisher-tag", cases[0].typeSource)
        assertEquals("Summary here", cases[0].summary)
        assertEquals(emptySet(), cases[1].expectedTechniques)
        assertNull(cases[1].summary)
    }

    @Test
    fun `beats reader keeps the beat, treats unknown type as none and ignores reviewer columns`() {
        val typed = listOf("NONE", "-", "id1", "politics", "The Hill", "opinion", "web-lookup:https://x", "https://x", "model", "", "Congress has never passed a real budget", "opinion section; rule 2")
        val unknown = listOf("SUBTLE", "curiosity_gap", "id2", "gaming", "PC Gamer", "unknown", "", "", "model", "unsure", "One of the scariest games I have played is out soon", "")
        val cases = HeadlineFiles.readBeats(temp("# header\n${typed.joinToString("\t")}\n${unknown.joinToString("\t")}\n"))

        assertEquals("politics", cases[0].beat)
        assertEquals("opinion", cases[0].articleType)
        assertEquals("Congress has never passed a real budget", cases[0].headline)
        assertNull(cases[1].articleType)
        assertNull(cases[1].typeSource)
        assertEquals(setOf(Category.CURIOSITY), cases[1].expectedTechniques)
    }

    @Test
    fun `context reader parses context columns including summary, section, and type`() {
        val row = listOf(
            "SUBTLE", "curiosity_gap", "ctx1", "science", "New Scientist", "news", "implicit",
            "https://example.com/story", "model", "", "Scientists discover why we dream", "note",
            "A new study reveals the brain mechanism behind dreams.", "Science", "NewsArticle", "ok",
            "https://example.com/story", "Story facts", "",
        )
        val cases = HeadlineFiles.readContext(temp("# header\n${row.joinToString("\t")}\n"))
        assertEquals(1, cases.size)
        assertEquals("ctx1", cases[0].id)
        assertEquals("Scientists discover why we dream", cases[0].headline)
        assertEquals("science", cases[0].beat)
        assertEquals("New Scientist", cases[0].outlet)
        assertEquals("news", cases[0].articleType)
        assertEquals("A new study reveals the brain mechanism behind dreams.", cases[0].summary)
        assertEquals("Science", cases[0].pageSection)
        assertEquals("NewsArticle", cases[0].pageType)
        assertEquals(setOf(Category.CURIOSITY), cases[0].expectedTechniques)
    }
}
