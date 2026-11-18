package com.medialiteracy.harvest

import java.io.File

/**
 * Feeds from an OPML export (a reader app's source list), used in place of the
 * panel when the question is how the rules do on what one reader actually reads.
 *
 * Feeds of one outlet ("NPR | Health", "NPR | World") share the outlet's name, so
 * the per-outlet quota and the sample's spread count the outlet once. Sources
 * that are not news headlines are left out: Wikipedia's daily features.
 *
 * A subreddit is kept: on a news subreddit a link post carries the article's
 * own headline, from whichever outlet was posted, so one feed samples many
 * outlets. [RedditLinks] turns each post into its article.
 */
internal object OpmlSources {
    const val KIND = "reader"
    private val SKIPPED_HOSTS = listOf("wikipedia.org")

    private val outline = Regex("""<outline\b[^>]*>""")
    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*"([^"]*)"""").find(tag)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifEmpty { null }

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    fun parse(opml: String): List<Source> = outline.findAll(opml).mapNotNull { match ->
        val url = attr(match.value, "xmlUrl") ?: return@mapNotNull null
        if (SKIPPED_HOSTS.any { it in url.substringAfter("://").substringBefore('/') }) return@mapNotNull null
        val name = (attr(match.value, "text") ?: attr(match.value, "title") ?: return@mapNotNull null).substringBefore(" | ").trim()
        Source(name, KIND, url)
    }.distinctBy { it.url }.toList()

    fun read(file: File): List<Source> {
        require(file.exists()) { "No source list at ${file.path}" }
        return parse(file.readText(Charsets.UTF_8)).also { require(it.isNotEmpty()) { "${file.path} lists no feeds" } }
    }
}

/**
 * A subreddit's feed gives each post's title and a link to the Reddit thread;
 * the article's address sits in the post body as the "[link]" anchor. This maps
 * thread address to article address, leaving out posts that link back to Reddit
 * (text posts, "ask us anything" threads).
 */
internal object RedditLinks {
    private val entry = Regex("""<entry>.*?</entry>""", RegexOption.DOT_MATCHES_ALL)
    private val thread = Regex("""<link\s+href="([^"]+)"""")
    private val article = Regex("""&lt;a href=&quot;(https?://(?:(?!&quot;).)+)&quot;&gt;\[link]""")
    private val redditHosts = listOf("reddit.com", "redd.it")

    fun isSubreddit(feedUrl: String): Boolean = host(feedUrl).let { it == "reddit.com" || it.endsWith(".reddit.com") }

    fun host(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase().removePrefix("www.")

    fun articleByThread(feedXml: String): Map<String, String> = entry.findAll(feedXml).mapNotNull { e ->
        val threadUrl = thread.find(e.value)?.groupValues?.get(1) ?: return@mapNotNull null
        val articleUrl = article.find(e.value)?.groupValues?.get(1)?.replace("&amp;amp;", "&")?.replace("&amp;", "&") ?: return@mapNotNull null
        if (redditHosts.any { host(articleUrl) == it || host(articleUrl).endsWith(".$it") }) return@mapNotNull null
        threadUrl to articleUrl
    }.toMap()
}
