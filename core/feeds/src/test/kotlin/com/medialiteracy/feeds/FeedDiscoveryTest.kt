package com.medialiteracy.feeds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedDiscoveryTest {

    @Test
    fun `finds an rss link with an absolute address`() {
        val html = """
            <html><head>
            <link rel="alternate" type="application/rss+xml" title="Feed" href="https://example.com/feed.xml">
            </head><body></body></html>
        """.trimIndent()

        assertEquals("https://example.com/feed.xml", FeedDiscovery.findFeedUrl(html, "https://example.com"))
    }

    @Test
    fun `resolves a root-relative link against the page it came from`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="/feed.xml">"""

        assertEquals("https://example.com/feed.xml", FeedDiscovery.findFeedUrl(html, "https://example.com/news"))
    }

    @Test
    fun `resolves a schemeless link`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="//example.com/feed.xml">"""

        assertEquals("https://example.com/feed.xml", FeedDiscovery.findFeedUrl(html, "https://example.com"))
    }

    @Test
    fun `resolves a bare relative link against a plain homepage`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="feed.xml">"""

        assertEquals("https://example.com/feed.xml", FeedDiscovery.findFeedUrl(html, "https://example.com"))
    }

    @Test
    fun `resolves a bare relative link against a page with its own path`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="feed.xml">"""

        assertEquals(
            "https://example.com/news/feed.xml",
            FeedDiscovery.findFeedUrl(html, "https://example.com/news/uk"),
        )
    }

    @Test
    fun `finds an atom link when there is no rss one`() {
        val html = """<link rel="alternate" type="application/atom+xml" href="https://example.com/atom.xml">"""

        assertEquals("https://example.com/atom.xml", FeedDiscovery.findFeedUrl(html, "https://example.com"))
    }

    @Test
    fun `ignores unrelated link tags`() {
        val html = """
            <link rel="stylesheet" href="https://example.com/style.css">
            <link rel="icon" href="/favicon.ico">
        """.trimIndent()

        assertNull(FeedDiscovery.findFeedUrl(html, "https://example.com"))
    }

    @Test
    fun `returns null for a page that declares no feed`() {
        assertNull(FeedDiscovery.findFeedUrl("<html><body>Hello</body></html>", "https://example.com"))
    }
}
