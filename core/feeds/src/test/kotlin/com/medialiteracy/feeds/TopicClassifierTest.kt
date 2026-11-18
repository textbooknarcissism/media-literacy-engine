package com.medialiteracy.feeds

import com.medialiteracy.model.Topic
import kotlin.test.Test
import kotlin.test.assertEquals

class TopicClassifierTest {

    @Test
    fun `classifies Daily Mail showbiz and entertainment paths`() {
        val topic = TopicClassifier.classify(
            url = "https://www.dailymail.co.uk/tvshowbiz/article-16146209/Heath-Ledger-Joker.html",
            categories = emptyList(),
            defaultTopic = Topic.POLITICS,
        )
        assertEquals(Topic.ENTERTAINMENT, topic)
    }

    @Test
    fun `classifies sports URL path`() {
        val topic = TopicClassifier.classify(
            url = "https://www.dailymail.co.uk/sport/football/article-16139115/bournemouth-liverpool.html",
            categories = emptyList(),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.SPORT, topic)
    }

    @Test
    fun `classifies politics from URL path`() {
        val topic = TopicClassifier.classify(
            url = "https://www.dailymail.co.uk/news/uk-politics/article-123456/election-update.html",
            categories = emptyList(),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.POLITICS, topic)
    }

    @Test
    fun `classifies from RSS category tags`() {
        val topic = TopicClassifier.classify(
            url = "https://example.com/article-123",
            categories = listOf("Film & Television", "Celebrity"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.ENTERTAINMENT, topic)
    }

    @Test
    fun `falls back to default topic when no category or path indicators`() {
        val topic = TopicClassifier.classify(
            url = "https://example.com/news/article-999",
            categories = listOf("General News"),
            defaultTopic = Topic.POLITICS,
        )
        assertEquals(Topic.POLITICS, topic)
    }

    @Test
    fun `does not misclassify words containing substring ai as SCIENCE_TECH`() {
        // "Ukraine", "Tony Blair", "Rural affairs", "Overseas aid", "Capital gains tax", "Hair loss", "Hawaii"
        // all contain 'ai' but are NOT Artificial Intelligence / Science & Tech!
        val ukraine = TopicClassifier.classify(
            url = "https://www.theguardian.com/world/2026/sep/27/russian-occupied-oleshky",
            categories = listOf("Ukraine", "Russia", "Europe", "World news"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.GENERAL, ukraine)

        val tonyBlair = TopicClassifier.classify(
            url = "https://www.theguardian.com/commentisfree/2026/sep/27/tony-blair-burnham",
            categories = listOf("Tony Blair", "Labour", "Brexit", "European Union", "Politics"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.POLITICS, tonyBlair)

        val overseasAid = TopicClassifier.classify(
            url = "https://www.theguardian.com/commentisfree/2026/sep/27/aid-cuts-reversed",
            categories = listOf("Aid", "United Nations", "Labour", "Politics"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.POLITICS, overseasAid)

        val consumerAffairs = TopicClassifier.classify(
            url = "https://www.theguardian.com/money/2026/sep/26/happy-birthday",
            categories = listOf("Consumer affairs", "Money"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.BUSINESS, consumerAffairs)

        val capitalGains = TopicClassifier.classify(
            url = "https://www.theguardian.com/money/2026/sep/25/capital-gains-tax",
            categories = listOf("Capital gains tax", "Tax and spending", "Budget"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.BUSINESS, capitalGains)
    }

    @Test
    fun `classifies genuine AI categories as SCIENCE_TECH`() {
        val ai = TopicClassifier.classify(
            url = "https://example.com/tech-article",
            categories = listOf("AI (artificial intelligence)", "Technology"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.SCIENCE_TECH, ai)

        val bareAi = TopicClassifier.classify(
            url = "https://example.com/tech-article",
            categories = listOf("AI", "Computing"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.SCIENCE_TECH, bareAi)
    }

    @Test
    fun `does not misclassify transport as SPORT`() {
        val transport = TopicClassifier.classify(
            url = "https://www.theguardian.com/uk-news/2026/sep/24/railway-assaults",
            categories = listOf("Rail transport", "Public transport", "Transport", "Crime", "UK news"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.GENERAL, transport)

        val bikes = TopicClassifier.classify(
            url = "https://www.theguardian.com/uk-news/2026/sep/24/lime-bikes",
            categories = listOf("Ebikes", "Transport", "London"),
            defaultTopic = Topic.GENERAL,
        )
        assertEquals(Topic.GENERAL, bikes)
    }
}
