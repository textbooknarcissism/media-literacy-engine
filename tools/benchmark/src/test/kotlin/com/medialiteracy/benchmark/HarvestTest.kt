package com.medialiteracy.benchmark

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class HarvestTest {
    private fun row(id: String, outlet: String, type: String) =
        IntakeRow(id, outlet, type, "section-feed", "https://feed", "https://x/$id", "2026-09-27", "Headline $id", "")

    private val rss = """<?xml version="1.0"?><rss version="2.0"><channel><title>T</title>
        <item><title>Headline one</title><link>https://example.com/a</link><description>&lt;p&gt;Summary one&lt;/p&gt;</description></item>
        </channel></rss>"""

    @Test
    fun `select drops links already seen and respects both caps`() {
        val existing = (1..Harvest.OUTLET_TYPE_CAP).map { row("e$it", "Daily Mail", "news") }
        val candidates = listOf(row("e1", "BBC News", "news"), row("new-mail-news", "Daily Mail", "news"), row("new-mail-opinion", "Daily Mail", "opinion"))
        assertEquals(listOf("new-mail-opinion"), Harvest.select(candidates, existing).map { it.id })
    }

    @Test
    fun `an item seen in several feeds keeps the first, most specific type`() {
        val intake = File.createTempFile("intake", ".tsv").apply { delete(); deleteOnExit() }
        Harvest.run(intake) { rss }
        val rows = IntakeRow.readAll(intake)
        assertEquals(1, rows.size)
        assertEquals("editorial", rows.single().articleType)
        assertEquals("Summary one", rows.single().summary)
    }

    @Test
    fun `a failing feed is reported and skipped, the rest still harvest`() {
        val intake = File.createTempFile("intake", ".tsv").apply { delete(); deleteOnExit() }
        val code = Harvest.run(intake) { url -> if ("editorials" in url) error("HTTP 503") else rss }
        assertEquals(0, code)
        assertEquals("letters", IntakeRow.readAll(intake).single().articleType)
    }

    @Test
    fun `harvest enriches rows with page metadata and fetch status`() {
        val intake = File.createTempFile("intake", ".tsv").apply { delete(); deleteOnExit() }
        val cacheDir = File.createTempFile("cache", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        val articleHtml = """<html><head><meta property="article:section" content="Environment"></head></html>"""
        Harvest.run(intake, cacheDir = cacheDir) { url ->
            if (url == "https://example.com/a") articleHtml else rss
        }
        val row = IntakeRow.readAll(intake).single()
        assertEquals("Environment", row.pageSection)
        assertEquals("ok", row.fetchStatus)
    }
}
