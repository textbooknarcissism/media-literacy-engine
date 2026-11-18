package com.medialiteracy.feeds

import com.medialiteracy.model.Topic

/**
 * Classifies an article into an editorial [Topic] using feed item metadata and URL section structure.
 *
 * Many major outlets (Daily Mail, The Guardian, BBC, NYT, CBS) publish multi-topic feeds
 * or global firehoses where an article's topic cannot be determined solely by the source.
 * This classifier inspects:
 * 1. RSS/Atom item <category> tags (e.g., "TV & Showbiz", "Football", "Politics")
 * 2. URL path segments (e.g., /tvshowbiz/, /sport/, /uk-politics/, /sciencetech/)
 * 3. Fallback to the subscription's default [Topic].
 */
object TopicClassifier {

    private val SPORT_REGEX = Regex(
        """(?i)\b(?:sports?|football|soccer|cricket|rugby|tennis|nba|nfl|mlb|formula\s*1|f1|golf|athletics|boxing|motorsport|premier\s+league|champions\s+league|cycling|baseball|basketball|hockey)\b"""
    )

    private val ENTERTAINMENT_REGEX = Regex(
        """(?i)\b(?:showbiz|celebrit(?:y|ies)|entertainment|femail|tv|television|films?|movies?|music|gossip|royals?|royal\s+family|culture|lifestyle|fashion|beauty|pop\s+culture|arts?|theatre|theater|comedy|books?|gaming|video\s+games?)\b"""
    )

    private val SCIENCE_TECH_REGEX = Regex(
        """(?i)\b(?:tech|technology|technologies|science|computing|gadgets?|ai|artificial\s+intelligence|generative\s+ai|genai|biotech|cybersecurity|software|hardware|robotics|astronomy|physics|chemistry|biology|neuroscience|spaceflight|space\s+exploration|outer\s+space|nasa|spacex)\b"""
    )

    private val BUSINESS_REGEX = Regex(
        """(?i)\b(?:business|finance|financial|economy|economic|economics|markets?|money|investing|banking|personal\s+finance|stocks?|shares?|property|real\s+estate|consumer\s+affairs|pensions?|tax|taxation)\b"""
    )

    private val POLITICS_REGEX = Regex(
        """(?i)\b(?:politics?|political|government|parliament|parliamentary|election|elections|congress|white\s+house|capitol|geopolitics|brexit|foreign\s+policy|labour|tory|tories|democrats?|republicans?|senate|diplomacy)\b"""
    )

    fun classify(
        url: String?,
        categories: List<String> = emptyList(),
        defaultTopic: Topic = Topic.GENERAL,
    ): Topic {
        // 1. Check item-level categories from feed XML
        for (category in categories) {
            val cat = category.trim()
            if (cat.isEmpty()) continue

            // Explicitly exclude "transport" / "public transport" / "rail transport" from sport matching
            val isSport = SPORT_REGEX.containsMatchIn(cat) && !cat.contains("transport", ignoreCase = true)
            if (isSport) return Topic.SPORT

            if (ENTERTAINMENT_REGEX.containsMatchIn(cat)) return Topic.ENTERTAINMENT
            if (SCIENCE_TECH_REGEX.containsMatchIn(cat)) return Topic.SCIENCE_TECH
            if (BUSINESS_REGEX.containsMatchIn(cat)) return Topic.BUSINESS
            if (POLITICS_REGEX.containsMatchIn(cat)) return Topic.POLITICS
        }

        // 2. Check URL path patterns
        if (!url.isNullOrBlank()) {
            val path = "/" + url.lowercase().substringAfter("://").substringAfter('/')
            when {
                path.startsWith("/sport") || path.contains("/sport/") || path.contains("/football") ||
                    path.contains("/soccer") || path.contains("/cricket") || path.contains("/rugby") ||
                    path.contains("/tennis") || path.contains("/formula1") || path.contains("/golf") -> return Topic.SPORT

                path.contains("/tvshowbiz") || path.contains("/showbiz") || path.contains("/celebrity") ||
                    path.contains("/femail") || path.contains("/entertainment") || path.contains("/film") ||
                    path.contains("/movies") || path.contains("/music") || path.contains("/culture") ||
                    path.contains("/lifestyle") || path.contains("/royals") || path.contains("/arts") ||
                    path.contains("/books") || path.contains("/stage") || path.contains("/style") || path.contains("/fashion") -> return Topic.ENTERTAINMENT

                path.contains("/sciencetech") || path.contains("/technology") || path.contains("/science") ||
                    path.contains("/tech/") || path.startsWith("tech/") -> return Topic.SCIENCE_TECH

                path.contains("/money") || path.contains("/business") || path.contains("/markets") ||
                    path.contains("/finance") || path.contains("/economy") -> return Topic.BUSINESS

                path.contains("/uk-politics") || path.contains("/us-politics") || path.contains("/politics") ||
                    path.contains("/congress") || path.contains("/election") || path.contains("/whitehouse") -> return Topic.POLITICS
            }
        }

        return defaultTopic
    }
}
