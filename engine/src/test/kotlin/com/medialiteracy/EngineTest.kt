package com.medialiteracy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineTest {

    @Test
    fun `all-in-one engine exposes core flagging, models, and feeds`() {
        val pack = RulePackLoader.loadDefault()
        assertNotNull(pack)
        assertEquals(39, pack.version)

        val flagger = HeadlineFlagger(pack)
        val result = flagger.flag("Scientists Discover One Weird Trick You Won't Believe")
        assertTrue(result.level != FlagLevel.NONE)
        assertTrue(result.hits.isNotEmpty())

        val resolver = SummaryContextResolver
        assertNotNull(resolver)

        val grouper = StoryGrouper
        assertNotNull(grouper)

        val comparator = StoryFramingComparator
        assertNotNull(comparator)

        assertEquals("manufactured_fear", Category.FEAR.id)
    }

    @Test
    fun `unified MediaLiteracyEngine facade provides one-stop API`() {
        val engine = MediaLiteracyEngine.default
        assertNotNull(engine)

        // 1. Flagging
        val flagged = engine.flag("Scientists Discover One Weird Trick You Won't Believe")
        assertTrue(flagged.level != FlagLevel.NONE)
        assertTrue(flagged.hits.isNotEmpty())

        // 2. Feed Parsing
        val sampleRss = """
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Breaking News Item</title>
                  <link>https://example.com/1</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val parsed = engine.parseFeed(sampleRss)
        assertEquals("Test Feed", parsed.title)
        assertEquals(1, parsed.items.size)

        // 3. Story Grouping
        val itemsBySource = mapOf(
            "Source A" to listOf(FeedItem("Boeing Strike Ends as Machinists Approve New Contract", null, null, null, null)),
            "Source B" to listOf(FeedItem("Boeing Machinists Ratify Deal to End Crippling Strike", null, null, null, null)),
        )
        val groups = engine.groupStories(itemsBySource)
        assertEquals(1, groups.size)

        // 4. Framing Comparison
        val comparison = engine.compareFraming(groups.single())
        assertEquals(2, comparison.comparisons.size)

        // 5. Context-Aware Flagging
        val opinionHeadline = "The prime minister's speech was tone-deaf and laughable"
        val unlabelled = engine.flag(opinionHeadline)
        assertTrue(Category.OPINION in unlabelled.categories)

        val withContext = engine.flag(
            opinionHeadline,
            ArticleContext(pageSection = "Opinion", pageType = "OpinionNewsArticle"),
        )
        assertTrue(Category.OPINION !in withContext.categories)
    }
}
