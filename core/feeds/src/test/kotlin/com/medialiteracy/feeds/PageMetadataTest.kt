package com.medialiteracy.feeds

import com.medialiteracy.model.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageMetadataTest {

    @Test
    fun `extracts section and author from standard HTML meta tags`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="article:section" content="Opinion">
                <meta name="author" content="Jane Smith">
                <meta property="og:type" content="article">
            </head>
            <body><p>Article content goes here</p></body>
            </html>
        """.trimIndent()

        val meta = PageMetadata.extract(html)

        assertEquals("Opinion", meta.section)
        assertEquals("Jane Smith", meta.author)
        assertEquals(ArticleType.OPINION, meta.articleType)
    }

    @Test
    fun `keeps an apostrophe inside a meta value`() {
        val html = """<head><meta property="article:section" content="Editors' picks"><meta content="Seán O'Brien" name="author"></head>"""
        val meta = PageMetadata.extract(html)
        assertEquals("Editors' picks", meta.section)
        assertEquals("Seán O'Brien", meta.author)
    }

    @Test
    fun `reads a list of JSON-LD types and prefers the one naming a genre`() {
        val html = """
            <script type="application/ld+json">
            {"@graph": [
                {"@type": "Organization", "name": "Example News"},
                {"@type": ["NewsArticle", "OpinionNewsArticle"], "articleSection": "Politics"}
            ]}
            </script>
        """.trimIndent()
        val meta = PageMetadata.extract(html)
        assertEquals("OpinionNewsArticle", meta.pageType)
        assertEquals(ArticleType.OPINION, meta.articleType)
    }

    @Test
    fun `a culture section on a plain news page names no type`() {
        val html = """<script type="application/ld+json">{"@type": "NewsArticle", "articleSection": "Culture"}</script>"""
        val meta = PageMetadata.extract(html)
        assertNull(meta.articleType)
        assertNull(meta.toArticleContext().resolvedArticleType)
    }

    @Test
    fun `extracts type and section from JSON-LD block`() {
        val html = """
            <html>
            <head>
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@type": "ReviewNewsArticle",
                    "articleSection": "Culture & Books",
                    "author": {
                        "@type": "Person",
                        "name": "Alex Writer"
                    }
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val meta = PageMetadata.extract(html)

        assertEquals("Culture & Books", meta.section)
        assertEquals("ReviewNewsArticle", meta.pageType)
        assertEquals("Alex Writer", meta.author)
        assertEquals(ArticleType.REVIEW, meta.articleType)
    }

    @Test
    fun `extracts type and section from JSON-LD graph structure`() {
        val html = """
            <html>
            <head>
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@graph": [
                        { "@type": "WebSite", "name": "News Site" },
                        {
                            "@type": "OpinionNewsArticle",
                            "articleSection": "Commentary",
                            "headline": "A view on events"
                        }
                    ]
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val meta = PageMetadata.extract(html)

        assertEquals("Commentary", meta.section)
        assertEquals("OpinionNewsArticle", meta.pageType)
        assertEquals(ArticleType.OPINION, meta.articleType)
    }

    @Test
    fun `falls back to URL path segments when meta tags are absent`() {
        val html = "<html><head><title>Some News</title></head><body><p>Text</p></body></html>"
        val url = "https://www.theguardian.com/commentisfree/2026/oct/12/example-piece"

        val meta = PageMetadata.extract(html, url = url)

        assertEquals("commentisfree", meta.section)
        assertEquals(ArticleType.OPINION, meta.articleType)
    }

    @Test
    fun `handles blank or missing HTML gracefully without throwing`() {
        val meta = PageMetadata.extract("")
        assertNull(meta.section)
        assertNull(meta.pageType)
        assertNull(meta.author)
        assertNull(meta.articleType)
    }

    @Test
    fun `toArticleContext maps all fields cleanly`() {
        val meta = PageMetadata(
            section = "Opinion",
            pageType = "OpinionNewsArticle",
            author = "Jane Smith",
            articleType = ArticleType.OPINION,
        )
        val ctx = meta.toArticleContext(summary = "Summary text", storyText = "Body text")
        assertEquals(ArticleType.OPINION, ctx.articleType)
        assertEquals(ArticleType.OPINION, ctx.resolvedArticleType)
        assertEquals("Opinion", ctx.pageSection)
        assertEquals("OpinionNewsArticle", ctx.pageType)
        assertEquals("Jane Smith", ctx.byline)
        assertEquals("Summary text", ctx.summary)
        assertEquals("Body text", ctx.storyText)
    }
}
