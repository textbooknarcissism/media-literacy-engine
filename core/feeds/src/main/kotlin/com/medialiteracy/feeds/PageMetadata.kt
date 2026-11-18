package com.medialiteracy.feeds

import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType

/**
 * Extracts publication metadata (section, page type, byline) from an article's raw HTML.
 *
 * Pure and offline: no network calls or Android dependencies.
 * Designed to provide context for codebook rule 2: distinguishing opinion,
 * analysis, reviews, and event reporting from headline phrasing alone.
 */
data class PageMetadata(
    val section: String? = null,
    val pageType: String? = null,
    val author: String? = null,
    val articleType: ArticleType? = null,
) {
    fun toArticleContext(summary: String? = null, storyText: String? = null): ArticleContext =
        ArticleContext(
            articleType = this.articleType,
            pageSection = this.section,
            pageType = this.pageType,
            byline = this.author,
            summary = summary,
            storyText = storyText,
        )

    companion object {

        private val META_SECTION = Regex(
            """<meta\s+[^>]*?(?:property|name)=["'](?:article:section|section)["'][^>]*?content=(["'])((?:(?!\1)[^>])+)\1""",
            RegexOption.IGNORE_CASE,
        )
        private val META_SECTION_REV = Regex(
            """<meta\s+[^>]*?content=(["'])((?:(?!\1)[^>])+)\1[^>]*?(?:property|name)=["'](?:article:section|section)["']""",
            RegexOption.IGNORE_CASE,
        )

        private val META_AUTHOR = Regex(
            """<meta\s+[^>]*?(?:property|name)=["'](?:author|article:author)["'][^>]*?content=(["'])((?:(?!\1)[^>])+)\1""",
            RegexOption.IGNORE_CASE,
        )
        private val META_AUTHOR_REV = Regex(
            """<meta\s+[^>]*?content=(["'])((?:(?!\1)[^>])+)\1[^>]*?(?:property|name)=["'](?:author|article:author)["']""",
            RegexOption.IGNORE_CASE,
        )

        private val JSON_LD_BLOCK = Regex(
            """<script\b[^>]*?type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        /** One "@type" value: a single name or a list such as ["NewsArticle", "OpinionNewsArticle"]. */
        private val JSON_LD_TYPE = Regex(
            """"@type"\s*:\s*(\[[^\]]*]|"[^"]*")""",
            RegexOption.IGNORE_CASE,
        )
        private val QUOTED_NAME = Regex(""""([^"]+)"""")
        private val ARTICLE_TYPE_NAME = Regex("""NewsArticle|Article|Posting|Review""", RegexOption.IGNORE_CASE)

        /** The article-like type in a JSON-LD block, preferring one that names a genre ("OpinionNewsArticle" over "NewsArticle"). */
        private fun articleTypeIn(block: String): String? {
            val names = JSON_LD_TYPE.findAll(block)
                .flatMap { QUOTED_NAME.findAll(it.groupValues[1]).map { name -> name.groupValues[1].trim() } }
                .filter { ARTICLE_TYPE_NAME.containsMatchIn(it) }
                .toList()
            return names.firstOrNull { ArticleType.fromPageType(it) != null } ?: names.firstOrNull()
        }

        private val JSON_LD_SECTION = Regex(
            """"articleSection"\s*:\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )

        private val JSON_LD_AUTHOR = Regex(
            """"author"\s*:\s*(?:\[\s*)?\{[^}]*?"name"\s*:\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )

        fun extract(html: String, url: String? = null): PageMetadata {
            if (html.isBlank()) {
                val inferredType = ArticleTypeClassifier.classify(url)
                return PageMetadata(
                    section = extractUrlSection(url),
                    articleType = inferredType,
                )
            }

            var section: String? = null
            var pageType: String? = null
            var author: String? = null

            // 1. Check HTML meta tags
            // Group 1 is the quote mark, so an apostrophe inside "Editors' picks" is kept.
            section = META_SECTION.find(html)?.groupValues?.get(2)?.trim()
                ?: META_SECTION_REV.find(html)?.groupValues?.get(2)?.trim()

            author = META_AUTHOR.find(html)?.groupValues?.get(2)?.trim()
                ?: META_AUTHOR_REV.find(html)?.groupValues?.get(2)?.trim()

            // 2. Check JSON-LD blocks
            for (match in JSON_LD_BLOCK.findAll(html)) {
                val block = match.groupValues[1]

                if (pageType == null) {
                    pageType = articleTypeIn(block)
                }
                if (section == null) {
                    section = JSON_LD_SECTION.find(block)?.groupValues?.get(1)?.trim()
                }
                if (author == null) {
                    author = JSON_LD_AUTHOR.find(block)?.groupValues?.get(1)?.trim()
                }
            }

            // 3. Fallback for section: URL path segment
            if (section == null && !url.isNullOrBlank()) {
                section = extractUrlSection(url)
            }

            // 4. Resolve ArticleType enum
            val resolvedArticleType = ArticleType.fromPageType(pageType)
                ?: ArticleTypeClassifier.classify(url, listOfNotNull(section))

            return PageMetadata(
                section = section?.decodeHtml(),
                pageType = pageType,
                author = author?.decodeHtml(),
                articleType = resolvedArticleType,
            )
        }

        private fun extractUrlSection(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val path = url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').substringBefore('#')
            val segments = path.split('/').filter { it.isNotBlank() }
            return segments.firstOrNull { seg ->
                ArticleTypeClassifier.classify("/$seg/") != null
            } ?: segments.firstOrNull()
        }

        private fun String.decodeHtml(): String = this
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&nbsp;", " ")
            .trim()
    }
}
