package com.medialiteracy.feeds

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Standard OPML (Outline Processor Markup Language) feed representation.
 * Conforms to the universal OPML 2.0 open standard.
 */
data class OpmlFeed(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String? = null,
    val category: String? = null,
)

object OpmlParser {

    /**
     * Parses an OPML 1.0 or 2.0 XML document into a list of [OpmlFeed] items.
     * Supports both flat feeds and nested folder categories.
     */
    fun parse(opmlXml: String): List<OpmlFeed> {
        if (opmlXml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            runCatching { isXIncludeAware = false }
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
        }

        val doc = runCatching {
            builder.parse(ByteArrayInputStream(opmlXml.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return emptyList()

        doc.documentElement.normalize()
        val results = mutableListOf<OpmlFeed>()

        val outlines = doc.getElementsByTagName("outline")
        for (i in 0 until outlines.length) {
            val node = outlines.item(i)
            if (node is Element) {
                val xmlUrl = node.getAttribute("xmlUrl").ifEmpty {
                    node.getAttribute("xmlurl").ifEmpty {
                        node.getAttribute("url")
                    }
                }.trim()

                if (xmlUrl.isNotEmpty() && (xmlUrl.startsWith("http://") || xmlUrl.startsWith("https://"))) {
                    val title = node.getAttribute("title").ifEmpty {
                        node.getAttribute("text")
                    }.trim().ifEmpty { xmlUrl }

                    val htmlUrl = node.getAttribute("htmlUrl").ifEmpty {
                        node.getAttribute("htmlurl")
                    }.trim().takeIf { it.isNotEmpty() }

                    // Determine category: either explicit attribute or enclosing parent outline's title
                    var category = node.getAttribute("category").trim().takeIf { it.isNotEmpty() }
                    if (category == null) {
                        val parent = node.parentNode
                        if (parent is Element && parent.tagName.equals("outline", ignoreCase = true)) {
                            category = parent.getAttribute("title").ifEmpty {
                                parent.getAttribute("text")
                            }.trim().takeIf { it.isNotEmpty() }
                        }
                    }

                    results.add(
                        OpmlFeed(
                            title = FeedParser.decodeHtmlEntities(title),
                            xmlUrl = xmlUrl,
                            htmlUrl = htmlUrl,
                            category = category?.let { FeedParser.decodeHtmlEntities(it) },
                        )
                    )
                }
            }
        }

        return results.distinctBy { it.xmlUrl }
    }
}

object OpmlExporter {

    /**
     * Exports feeds into a standard OPML 2.0 XML string.
     */
    fun export(
        title: String = "Subscriptions",
        feeds: List<OpmlFeed>,
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>${escapeXml(title)}</title>\n")
        sb.append("    <dateCreated>${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}</dateCreated>\n")
        sb.append("    <docs>http://opml.org/spec2.opml</docs>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")

        val grouped = feeds.groupBy { it.category?.ifBlank { "Uncategorized" } ?: "Uncategorized" }

        for ((category, groupFeeds) in grouped) {
            val isRootFolder = category != "Uncategorized"
            if (isRootFolder) {
                sb.append("    <outline text=\"${escapeXml(category)}\" title=\"${escapeXml(category)}\">\n")
            }

            val indent = if (isRootFolder) "      " else "    "
            for (feed in groupFeeds) {
                sb.append("${indent}<outline type=\"rss\" ")
                sb.append("text=\"${escapeXml(feed.title)}\" ")
                sb.append("title=\"${escapeXml(feed.title)}\" ")
                sb.append("xmlUrl=\"${escapeXml(feed.xmlUrl)}\" ")
                if (!feed.htmlUrl.isNullOrBlank()) {
                    sb.append("htmlUrl=\"${escapeXml(feed.htmlUrl)}\" ")
                }
                if (!feed.category.isNullOrBlank()) {
                    sb.append("category=\"${escapeXml(feed.category)}\" ")
                }
                sb.append("/>\n")
            }

            if (isRootFolder) {
                sb.append("    </outline>\n")
            }
        }

        sb.append("  </body>\n")
        sb.append("</opml>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
