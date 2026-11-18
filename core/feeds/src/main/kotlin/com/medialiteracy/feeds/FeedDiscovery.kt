package com.medialiteracy.feeds

/**
 * Finds a site's own feed address from its homepage, the same way any RSS
 * reader does: sites that offer a feed say so in their page's `<head>`, with
 * a line like `<link rel="alternate" type="application/rss+xml" href="...">`.
 * This means a reader typing in "bbc.com" rather than the exact feed address
 * can still be pointed at the right place.
 *
 * No network access here, and no Android: give it a page's HTML and the
 * address it came from, get a feed address back (or null). The caller does
 * the fetching, same split as [ArticleText].
 */
object FeedDiscovery {

    private val LINK_TAG = Regex(
        """<link\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    private val FEED_TYPE = Regex(
        """type\s*=\s*["'](application/rss\+xml|application/atom\+xml)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val HREF = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * @param pageUrl the address [html] was fetched from, needed to resolve a
     *   feed link given as a path rather than a full address (e.g. "/rss.xml").
     * @return the first feed address the page declares, or null if it
     *   declares none.
     */
    fun findFeedUrl(html: String, pageUrl: String): String? {
        val link = LINK_TAG.findAll(html)
            .map { it.value }
            .firstOrNull { FEED_TYPE.containsMatchIn(it) }
            ?: return null

        val href = HREF.find(link)?.groupValues?.get(1) ?: return null
        return resolve(pageUrl, href)
    }

    /** Turns a relative link ("/feed", "feed", "//example.com/feed") into an absolute one. */
    private fun resolve(pageUrl: String, href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> pageUrl.substringBefore("//") + "//" + href.removePrefix("//")
        href.startsWith("/") -> host(pageUrl) + href
        // A bare relative path ("feed.xml"), resolved against whatever comes
        // after the host in the page's own address, if anything.
        else -> {
            val afterHost = pageUrl.removePrefix(host(pageUrl))
            val directory = if (afterHost.contains('/')) afterHost.substringBeforeLast('/') else ""
            host(pageUrl) + directory + "/" + href
        }
    }

    /** The scheme and domain only, e.g. "https://example.com" from "https://example.com/news/uk". */
    private fun host(pageUrl: String): String {
        val schemeEnd = pageUrl.indexOf("//") + 2
        val hostEnd = pageUrl.indexOf('/', schemeEnd).let { if (it == -1) pageUrl.length else it }
        return pageUrl.take(hostEnd)
    }
}
