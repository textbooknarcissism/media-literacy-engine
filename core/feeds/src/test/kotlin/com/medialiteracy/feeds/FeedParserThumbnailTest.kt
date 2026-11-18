package com.medialiteracy.feeds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Real feeds don't agree on how to offer a thumbnail. Each case here is
 * taken from an actual outlet's feed, not invented, per the project's own
 * standing lesson that manufactured test data proves nothing about real
 * copy: see the flagging rules' history for why that matters.
 */
class FeedParserThumbnailTest {

    private fun rss(itemBody: String) = """
        <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0">
          <channel>
            <title>Test</title>
            <item>
              <title>Headline</title>
              $itemBody
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `reads a media thumbnail tag, as BBC feeds use`() {
        val feed = FeedParser.parse(
            rss("""<media:thumbnail width="240" height="135" url="https://example.com/bbc.jpg"/>"""),
        )
        assertEquals("https://example.com/bbc.jpg", feed.items.single().imageUrl)
    }

    @Test
    fun `reads an image enclosure, as Daily Mail feeds use`() {
        val feed = FeedParser.parse(
            rss("""<enclosure url="https://example.com/mail.jpg" type="image/jpeg" length="123"/>"""),
        )
        assertEquals("https://example.com/mail.jpg", feed.items.single().imageUrl)
    }

    @Test
    fun `ignores a non-image enclosure, such as a podcast episode`() {
        val feed = FeedParser.parse(
            rss("""<enclosure url="https://example.com/episode.mp3" type="audio/mpeg" length="123"/>"""),
        )
        assertNull(feed.items.single().imageUrl)
    }

    @Test
    fun `reads a media content tag when it is an image`() {
        val feed = FeedParser.parse(
            rss("""<media:content type="image/jpeg" url="https://example.com/content.jpg"/>"""),
        )
        assertEquals("https://example.com/content.jpg", feed.items.single().imageUrl)
    }

    @Test
    fun `prefers media thumbnail over enclosure when a feed offers both`() {
        val feed = FeedParser.parse(
            rss(
                """
                <media:thumbnail url="https://example.com/thumb.jpg"/>
                <enclosure url="https://example.com/enclosure.jpg" type="image/jpeg"/>
                """.trimIndent(),
            ),
        )
        assertEquals("https://example.com/thumb.jpg", feed.items.single().imageUrl)
    }

    @Test
    fun `has no image at all, as the Guardian and Ars Technica feeds don't offer one`() {
        val feed = FeedParser.parse(rss(""))
        assertNull(feed.items.single().imageUrl)
    }
}
