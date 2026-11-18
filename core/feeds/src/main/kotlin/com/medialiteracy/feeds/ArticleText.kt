package com.medialiteracy.feeds

/**
 * Pulls the readable body text out of an article's HTML.
 *
 * Deliberately crude. A proper reader-mode extractor is a large piece of work,
 * and this does not need to be one: context resolution only needs enough of the
 * article to know what actually happened and what caveats the piece contains.
 * The opening few paragraphs carry both, which is also where a buried
 * "in mice" or "early-stage" qualifier lives.
 *
 * No network access here, and no Android (give it HTML, get text back) so
 * the iPhone version reuses it unchanged.
 */
object ArticleText {

    /** Wholesale removals: these contain no prose and plenty of noise. */
    private val JUNK_BLOCKS = Regex(
        """<(script|style|noscript|svg|head|nav|header|footer|aside|form|figure)\b[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val COMMENTS = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

    /** The main body, when a page is marked up honestly enough to say so. */
    private val ARTICLE_BLOCK = Regex(
        """<article\b[^>]*>(.*?)</article>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val PARAGRAPH = Regex(
        """<p\b[^>]*>(.*?)</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val TAG = Regex("""<[^>]+>""")

    /**
     * @param maxChars stop once this much text has been collected. The opening
     *   of a piece is what matters for context and caveat extraction; parsing
     *   the whole page carries diminishing returns.
     */
    fun extract(html: String, maxChars: Int = 4000): String {
        val cleaned = html
            .replace(COMMENTS, " ")
            .replace(JUNK_BLOCKS, " ")

        // Prefer the marked-up article body; fall back to the whole page.
        val scope = ARTICLE_BLOCK.find(cleaned)?.groupValues?.get(1) ?: cleaned

        val paragraphs = PARAGRAPH.findAll(scope)
            .map { stripTags(it.groupValues[1]) }
            // Nav links, captions and cookie notices all survive as very short
            // fragments. Real prose does not come in five-word paragraphs.
            .filter { it.length > 60 }
            .toList()

        val body = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            // Some sites don't use <p> at all. Falling back to the whole
            // stripped page is ugly but better than sending nothing.
            stripTags(scope)
        }

        return body.take(maxChars).trim()
    }

    private fun stripTags(fragment: String): String = fragment
        .replace(TAG, " ")
        .let(::decodeEntities)
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Only the handful that actually show up in body copy. */
    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
}
