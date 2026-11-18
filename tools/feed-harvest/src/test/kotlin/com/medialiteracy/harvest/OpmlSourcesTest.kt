package com.medialiteracy.harvest

import kotlin.test.Test
import kotlin.test.assertEquals

class OpmlSourcesTest {

    @Test
    fun `a subreddit post stands for the article it links to`() {
        val xml = """<feed><entry><content type="html">&lt;a href=&quot;https://www.reddit.com/r/politics/comments/1/a/&quot;&gt; img &lt;/a&gt; &lt;a href=&quot;https://thehill.com/a?x=1&amp;amp;y=2&quot;&gt;[link]&lt;/a&gt;</content><link href="https://www.reddit.com/r/politics/comments/1/a/" /><title>A</title></entry>
            <entry><content type="html">&lt;a href=&quot;https://www.reddit.com/r/politics/comments/2/b/&quot;&gt;[link]&lt;/a&gt;</content><link href="https://www.reddit.com/r/politics/comments/2/b/" /><title>Ask us anything</title></entry></feed>"""
        assertEquals(mapOf("https://www.reddit.com/r/politics/comments/1/a/" to "https://thehill.com/a?x=1&y=2"), RedditLinks.articleByThread(xml))
        assertEquals("thehill.com", RedditLinks.host("https://www.thehill.com/a"))
        assertEquals(true, RedditLinks.isSubreddit("https://www.reddit.com/r/politics/.rss"))
    }

    @Test
    fun `feeds of one outlet share its name and non-news sources are left out`() {
        val opml = """
            <opml version="2.0"><body>
              <outline text="News">
                <outline type="rss" text="NPR | Health" xmlUrl="https://feeds.npr.org/1128/rss.xml"/>
                <outline type="rss" text="NPR | World" xmlUrl="https://feeds.npr.org/1004/rss.xml"/>
                <outline type="rss" text="Vox" title="Vox" xmlUrl="https://vox.com/rss/index.xml" htmlUrl="https://vox.com"/>
                <outline type="rss" text="Wikipedia | On This Day" xmlUrl="https://en.wikipedia.org/w/api.php?action=featuredfeed&amp;feed=onthisday"/>
              </outline>
            </body></opml>
        """.trimIndent()

        assertEquals(
            listOf(
                Source("NPR", "reader", "https://feeds.npr.org/1128/rss.xml"),
                Source("NPR", "reader", "https://feeds.npr.org/1004/rss.xml"),
                Source("Vox", "reader", "https://vox.com/rss/index.xml"),
            ),
            OpmlSources.parse(opml),
        )
    }
}
