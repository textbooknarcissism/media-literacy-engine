package com.medialiteracy.feeds

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeedParserResilienceTest {

    @Test
    fun `parses single-digit day and named timezones in RFC-822 dates`() {
        val date1 = FeedParser.parseDate("5 Sep 2026 14:02:10 EDT")
        assertNotNull(date1)

        val date2 = FeedParser.parseDate("Sun, 6 Sep 2026 09:15:00 BST")
        assertNotNull(date2)

        val date3 = FeedParser.parseDate("19 Sep 2026 18:30:00 GMT")
        assertNotNull(date3)

        val date4 = FeedParser.parseDate("Fri, 18 Sep 2026 12:00:00 -0400")
        assertNotNull(date4)

        val dateIso = FeedParser.parseDate("2026-09-19 14:02:10")
        assertNotNull(dateIso)

        // Global timezones (Australasian, Asian, European)
        val dateAest = FeedParser.parseDate("Mon, 21 Sep 2026 10:00:00 AEST")
        assertNotNull(dateAest)

        val dateJst = FeedParser.parseDate("Mon, 21 Sep 2026 09:00:00 JST")
        assertNotNull(dateJst)

        val dateIst = FeedParser.parseDate("Mon, 21 Sep 2026 05:30:00 IST")
        assertNotNull(dateIst)

        val dateNzdt = FeedParser.parseDate("Mon, 21 Sep 2026 13:00:00 NZDT")
        assertNotNull(dateNzdt)

        val dateEet = FeedParser.parseDate("Mon, 21 Sep 2026 02:00:00 EET")
        assertNotNull(dateEet)
    }

    @Test
    fun `decodes HTML entities in titles and summaries`() {
        val cleaned = FeedParser.clean("Trump &amp; Harris meet in &#8216;historic&#8217; debate &mdash; live updates &quot;now&quot;")
        assertEquals("Trump & Harris meet in ‘historic’ debate — live updates \"now\"", cleaned)
    }

    @Test
    fun `strips UTF-8 BOM and leading whitespace before XML declaration`() {
        val xmlWithBom = "\uFEFF   \n\r\t<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\"><channel><title>BOM Test</title><item><title>Story 1</title><link>https://example.com/1</link></item></channel></rss>"
        val parsed = FeedParser.parse(xmlWithBom)
        assertEquals("BOM Test", parsed.title)
        assertEquals(1, parsed.items.size)
        assertEquals("Story 1", parsed.items.first().title)
    }

    @Test
    fun `extracts category elements from RSS items and Atom entries`() {
        val rssWithCategories = """
            <rss version="2.0">
              <channel>
                <title>Category Test</title>
                <item>
                  <title>Article with categories</title>
                  <link>https://example.com/1</link>
                  <category>TV &amp; Showbiz</category>
                  <category>Celebrity</category>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val parsed = FeedParser.parse(rssWithCategories)
        assertEquals(listOf("TV & Showbiz", "Celebrity"), parsed.items.first().categories)
    }

    @Test
    fun `extracts image from img tag in description HTML when no enclosure is present`() {
        val rssWithHtmlImg = """
            <rss version="2.0">
              <channel>
                <title>Blog Feed</title>
                <item>
                  <title>Article with embedded image</title>
                  <link>https://example.com/art1</link>
                  <description><![CDATA[<p>Some text</p><img src="https://example.com/images/story.jpg" alt="pic" /><p>More text</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rssWithHtmlImg)
        assertEquals("https://example.com/images/story.jpg", parsed.items.first().imageUrl)
    }

    @Test
    fun `reads content encoded when description is omitted`() {
        val rssWithContentEncoded = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Feed</title>
                <item>
                  <title>Full Article</title>
                  <link>https://example.com/2</link>
                  <content:encoded><![CDATA[<p>This is the full article content with &#8220;smart quotes&#8221;.</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rssWithContentEncoded)
        assertEquals("This is the full article content with “smart quotes”.", parsed.items.first().summary)
    }

    @Test
    fun `parses ISO date-only format used in Nature and Dublin Core`() {
        val date = FeedParser.parseDate("2026-09-18")
        assertNotNull(date)
    }

    @Test
    fun `extracts media content with medium=image as used by NYTimes`() {
        val rss = """
            <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0">
              <channel>
                <title>NYT</title>
                <item>
                  <title>NYT Story</title>
                  <link>https://example.com/nyt</link>
                  <media:content height="1800" medium="image" url="https://example.com/nyt_image.jpg" width="1800"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rss)
        assertEquals("https://example.com/nyt_image.jpg", parsed.items.first().imageUrl)
    }

    @Test
    fun `extracts media content url without type or medium attribute as used by The Guardian`() {
        val rss = """
            <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0">
              <channel>
                <title>Guardian</title>
                <item>
                  <title>Guardian Story</title>
                  <link>https://example.com/guardian</link>
                  <media:content width="460" url="https://example.com/guardian_image.jpg"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rss)
        assertEquals("https://example.com/guardian_image.jpg", parsed.items.first().imageUrl)
    }

    @Test
    fun `extracts media thumbnail or content nested inside media group`() {
        val rss = """
            <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0">
              <channel>
                <title>CNN</title>
                <item>
                  <title>CNN Story</title>
                  <link>https://example.com/cnn</link>
                  <media:group>
                    <media:content medium="image" url="https://example.com/group_image.jpg" width="1100"/>
                  </media:group>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rss)
        assertEquals("https://example.com/group_image.jpg", parsed.items.first().imageUrl)
    }

    @Test
    fun `strips trailing RSS boilerplate like Continue reading and syndication footers from summary`() {
        val rssWithTrailing = """
            <rss version="2.0">
              <channel>
                <title>Feed</title>
                <item>
                  <title>Article with trailing boilerplate</title>
                  <link>https://example.com/3</link>
                  <description><![CDATA[<p>Delegates gathered in Liverpool for the annual conference.</p> <a href="https://example.com/story">Continue reading...</a>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rssWithTrailing)
        assertEquals("Delegates gathered in Liverpool for the annual conference.", parsed.items.first().summary)
    }
}
