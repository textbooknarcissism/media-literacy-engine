package com.medialiteracy.harvest

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextIntakeTest {

    private fun feed(vararg items: Pair<String, String>): String =
        "<rss><channel>" + items.joinToString("") { (title, link) ->
            "<item><title>$title</title><link>$link</link><description>Summary of $title and more words here.</description></item>"
        } + "</channel></rss>"

    private fun row(outlet: String, headline: String, link: String = "https://$outlet.example/$headline") =
        ContextRow(
            id = link.filter { it.isLetterOrDigit() }, beat = "politics", outlet = outlet, articleType = "", typeSource = "", feedUrl = "f",
            link = link, harvestedAt = "2026-10-12", headline = headline, summary = "s",
        )

    private fun tempDir() = File.createTempFile("ctxcache", "dir").apply { delete(); mkdirs(); deleteOnExit() }

    @Test
    fun `quota is per outlet and the draw repeats`() {
        val rows = (1..10).map { row("A", "headline $it") } + (1..10).map { row("B", "other $it") }
        val first = ContextIntake.select(rows, emptyList(), perOutlet = 3)
        assertEquals(3, first.count { it.outlet == "A" })
        assertEquals(3, first.count { it.outlet == "B" })
        assertEquals(first, ContextIntake.select(rows.reversed(), emptyList(), perOutlet = 3))
    }

    @Test
    fun `existing rows count against the quota and are never repeated`() {
        val rows = (1..6).map { row("A", "h$it") }
        val existing = rows.take(2)
        val picked = ContextIntake.select(rows, existing, perOutlet = 3)
        assertEquals(1, picked.size)
        assertTrue(picked.none { it in existing })
    }

    @Test
    fun `enrich reads page metadata, caches it, and lets the page fill a missing type`() {
        val cache = tempDir()
        val html = """<head><meta property="article:section" content="Opinion"></head>"""
        val out = ContextIntake.enrich(row("A", "h1", "https://a.example/x"), cache, { false }, { html })
        assertEquals("ok", out.fetchStatus)
        assertEquals("Opinion", out.pageSection)
        assertEquals("opinion", out.articleType)
        assertEquals("page", out.typeSource)
        assertTrue(File(cache, out.id + ".html").exists())
    }

    @Test
    fun `blocked hosts are marked and never fetched`() {
        var fetched = false
        val out = ContextIntake.enrich(row("FT", "h"), tempDir(), { true }, { fetched = true; "x" })
        assertEquals("blocked", out.fetchStatus)
        assertFalse(fetched)
    }

    @Test
    fun `a failed fetch is recorded`() {
        assertEquals("failed", ContextIntake.enrich(row("A", "h"), tempDir(), { false }, { null }).fetchStatus)
    }

    @Test
    fun `run writes a blind file with no detector label and can be resumed`() {
        val out = File.createTempFile("intake", ".tsv").apply { delete(); deleteOnExit() }
        val panel = listOf(Source("Paper", "politics", "https://paper.example/rss"))
        val xml = feed("The PM was tone-deaf" to "https://paper.example/1", "Second story here" to "https://paper.example/2")
        val args = { n: Int ->
            ContextIntake.run(out, panel, n, tempDir(), { xml }, { "<html></html>" }, { false }, today = "2026-10-12")
        }
        assertEquals(0, args(1))
        val first = ContextRow.readAll(out)
        assertEquals(1, first.size)
        assertEquals("politics", first.single().beat)
        assertEquals("ok", first.single().fetchStatus)
        assertFalse(out.readLines().any { it.split('\t').firstOrNull() in setOf("NONE", "SUBTLE", "PROMINENT") })

        args(2)
        assertEquals(2, ContextRow.readAll(out).size)
    }

    @Test
    fun `since keeps items on or after the cut-off and drops undated ones`() {
        val xml = "<rss><channel>" +
            "<item><title>Old story</title><link>https://p.example/1</link><pubDate>Sat, 10 Oct 2026 09:00:00 GMT</pubDate></item>" +
            "<item><title>New story</title><link>https://p.example/2</link><pubDate>Tue, 13 Oct 2026 09:00:00 GMT</pubDate></item>" +
            "<item><title>Undated story</title><link>https://p.example/3</link></item>" +
            "</channel></rss>"
        val panel = listOf(Source("Paper", "politics", "https://p.example/rss"))
        val since = parseSince(arrayOf("--since=2026-10-12"))
        val rows = ContextIntake.candidates(panel, { xml }, "2026-10-19", since, log = {})
        assertEquals(listOf("New story"), rows.map { it.headline })
        assertEquals(3, ContextIntake.candidates(panel, { xml }, "2026-10-19", null, log = {}).size)
    }

    @Test
    fun `rows already labelled in another set are skipped`() {
        val dir = tempDir()
        val id = "a".repeat(64)
        File(dir, "older-set.tsv").writeText("# header\nNONE\t-\t$id\tpolitics\tPaper\n")
        val out = File(dir, "new-intake.tsv")
        assertEquals(setOf(id), ContextIntake.labelledIds(out))
        val rows = listOf(row("A", "kept"), row("A", "seen").copy(id = id))
        assertEquals(listOf("kept"), ContextIntake.select(rows, emptyList(), perOutlet = 5, alreadyLabelled = setOf(id)).map { it.headline })
    }
}
