package com.medialiteracy.benchmark

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleCacheTest {

    @Test
    fun `fetches, writes cache file, and extracts metadata`() {
        val cacheDir = File.createTempFile("cache", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        val html = """
            <html>
            <head>
                <meta property="article:section" content="Science">
            </head>
            <body><p>Story</p></body>
            </html>
        """.trimIndent()

        val meta = ArticleCache.fetchAndExtract(
            id = "test1",
            url = "https://example.com/story",
            cacheDir = cacheDir,
            fetch = { html },
        )

        assertEquals("Science", meta.section)
        assertEquals("ok", meta.fetchStatus)
        assertTrue(File(cacheDir, "test1.html").exists())
    }

    @Test
    fun `reads from cache without calling fetch when file exists`() {
        val cacheDir = File.createTempFile("cache", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        val cachedHtml = """<html><head><meta property="article:section" content="CachedSection"></head></html>"""
        File(cacheDir, "cached1.html").writeText(cachedHtml)

        var fetchCalled = false
        val meta = ArticleCache.fetchAndExtract(
            id = "cached1",
            url = "https://example.com/story",
            cacheDir = cacheDir,
            fetch = {
                fetchCalled = true
                error("Should not be called")
            },
        )

        assertEquals("CachedSection", meta.section)
        assertEquals("ok", meta.fetchStatus)
        assertEquals(false, fetchCalled)
    }

    @Test
    fun `marks bot blocked domains without calling fetch`() {
        val cacheDir = File.createTempFile("cache", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        var fetchCalled = false

        val meta = ArticleCache.fetchAndExtract(
            id = "axios1",
            url = "https://www.axios.com/2026/10/12/example",
            cacheDir = cacheDir,
            fetch = {
                fetchCalled = true
                "html"
            },
        )

        assertEquals("blocked", meta.fetchStatus)
        assertEquals(false, fetchCalled)
    }

    @Test
    fun `matches blocked outlets by host, not by any text in the link`() {
        assertTrue(ArticleCache.isBotBlocked("https://www.ft.com/content/abc"))
        assertTrue(ArticleCache.isBotBlocked("https://ft.com/content/abc"))
        assertEquals(false, ArticleCache.isBotBlocked("https://news.microsoft.com/story"))
        assertEquals(false, ArticleCache.isBotBlocked("https://example.com/?ref=ft.com"))
    }

    @Test
    fun `records error status when fetch fails`() {
        val cacheDir = File.createTempFile("cache", "dir").apply { delete(); mkdirs(); deleteOnExit() }

        val meta = ArticleCache.fetchAndExtract(
            id = "fail1",
            url = "https://example.com/missing",
            cacheDir = cacheDir,
            fetch = { error("HTTP 404 for https://example.com/missing") },
        )

        assertEquals("not_found", meta.fetchStatus)
    }
}
