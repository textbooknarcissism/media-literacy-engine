package com.medialiteracy.feeds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpmlTest {

    @Test
    fun `parses standard OPML 2_0 with nested category outlines`() {
        val sampleOpml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head>
                <title>My Subscriptions</title>
              </head>
              <body>
                <outline text="Tech News" title="Tech News">
                  <outline type="rss" text="Ars Technica" title="Ars Technica" xmlUrl="https://feeds.arstechnica.com/arstechnica/index" htmlUrl="https://arstechnica.com"/>
                  <outline type="rss" text="The Verge" title="The Verge" xmlUrl="https://www.theverge.com/rss/index.xml" htmlUrl="https://theverge.com"/>
                </outline>
                <outline text="General News" title="General News">
                  <outline type="rss" text="BBC News" title="BBC News" xmlUrl="https://feeds.bbci.co.uk/news/rss.xml" htmlUrl="https://bbc.com"/>
                </outline>
              </body>
            </opml>
        """.trimIndent()

        val parsed = OpmlParser.parse(sampleOpml)
        assertEquals(3, parsed.size)

        assertEquals("Ars Technica", parsed[0].title)
        assertEquals("https://feeds.arstechnica.com/arstechnica/index", parsed[0].xmlUrl)
        assertEquals("Tech News", parsed[0].category)

        assertEquals("The Verge", parsed[1].title)
        assertEquals("Tech News", parsed[1].category)

        assertEquals("BBC News", parsed[2].title)
        assertEquals("General News", parsed[2].category)
    }

    @Test
    fun `exports and round trips OPML 2_0 feeds`() {
        val feeds = listOf(
            OpmlFeed(title = "BBC News", xmlUrl = "https://feeds.bbci.co.uk/news/rss.xml", htmlUrl = "https://bbc.com", category = "General"),
            OpmlFeed(title = "Ars Technica", xmlUrl = "https://feeds.arstechnica.com/arstechnica/index", htmlUrl = "https://arstechnica.com", category = "Science & Tech"),
        )

        val xml = OpmlExporter.export("Test Export", feeds)
        assertTrue(xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml.contains("xmlUrl=\"https://feeds.bbci.co.uk/news/rss.xml\""))

        val roundTrip = OpmlParser.parse(xml)
        assertEquals(2, roundTrip.size)
        assertEquals("BBC News", roundTrip[0].title)
        assertEquals("https://feeds.bbci.co.uk/news/rss.xml", roundTrip[0].xmlUrl)
        assertEquals("General", roundTrip[0].category)
    }

    @Test
    fun `parses flat OPML feeds without category wrappers`() {
        val flatOpml = """
            <opml version="1.0">
              <body>
                <outline text="PBS" xmlUrl="https://www.pbs.org/newshour/feeds/rss/headlines"/>
                <outline text="UPI" xmlUrl="https://rss.upi.com/news/top_news.rss"/>
              </body>
            </opml>
        """.trimIndent()

        val parsed = OpmlParser.parse(flatOpml)
        assertEquals(2, parsed.size)
        assertEquals("PBS", parsed[0].title)
        assertEquals("https://www.pbs.org/newshour/feeds/rss/headlines", parsed[0].xmlUrl)
        assertEquals("UPI", parsed[1].title)
    }
}
