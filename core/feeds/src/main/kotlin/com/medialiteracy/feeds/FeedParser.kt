package com.medialiteracy.feeds

import com.medialiteracy.model.FeedItem
import com.medialiteracy.model.ParsedFeed
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Turns feed XML into articles.
 *
 * Handles both formats sites actually use (RSS and Atom) because you don't
 * get to choose, and a reader that only speaks one of them looks broken on
 * roughly half the internet.
 *
 * This deliberately does no network work. Give it text, get articles back.
 * Fetching differs on every platform; parsing doesn't, so parsing lives here
 * where the iPhone version can reuse it.
 */
object FeedParser {

    fun parse(xml: String): ParsedFeed {
        if (xml.isBlank()) {
            return ParsedFeed(title = null, siteLink = null, items = emptyList())
        }
        // Feeds frequently arrive with UTF-8 BOM, leading blank lines, or XML prolog quirks
        val cleanedXml = sanitizeXml(xml)
        if (cleanedXml.isBlank()) {
            return ParsedFeed(title = null, siteLink = null, items = emptyList())
        }
        val doc = documentBuilder().parse(ByteArrayInputStream(cleanedXml.toByteArray(Charsets.UTF_8)))
        doc.documentElement.normalize()

        val root = doc.documentElement
        return when {
            // Atom calls the whole thing <feed> and each article an <entry>.
            root.tagName.equalsIgnoreCase("feed") -> parseAtom(root)
            // RSS wraps everything in <channel>, with each article an <item>.
            else -> parseRss(root)
        }
    }

    private fun sanitizeXml(raw: String): String {
        val trimmed = raw.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val firstTag = trimmed.indexOf('<')
        return if (firstTag > 0) trimmed.substring(firstTag) else trimmed
    }

    private fun parseRss(root: Element): ParsedFeed {
        // RSS 2.0 nests items under <channel>; RSS 1.0 puts them at the top level.
        val channel = root.child("channel") ?: root
        return ParsedFeed(
            title = clean(channel.childText("title")),
            siteLink = channel.childText("link"),
            items = root.descendants("item").map { item ->
                val desc = item.childText("description")
                val content = item.childText("content:encoded") ?: item.childText("content")
                val images = item.images(desc, content)
                FeedItem(
                    title = clean(item.childText("title")).orEmpty(),
                    link = item.childText("link") ?: item.childText("guid"),
                    publishedAt = parseDate(
                        item.childText("pubDate")
                            ?: item.childText("dc:date")
                            ?: item.childText("date")
                    ),
                    summary = clean(desc ?: content),
                    id = item.childText("guid") ?: item.childText("link"),
                    imageUrl = images.thumbnail,
                    fullImageUrl = images.full,
                    categories = item.categories(),
                )
            }.filter { it.title.isNotBlank() },
        )
    }

    private fun parseAtom(root: Element): ParsedFeed = ParsedFeed(
        title = clean(root.child("title")?.textContent),
        siteLink = root.linkHref(),
        items = root.descendants("entry").map { entry ->
            val summaryText = entry.childText("summary")
            val contentText = entry.childText("content")
            val images = entry.images(summaryText, contentText)
            FeedItem(
                title = clean(entry.childText("title")).orEmpty(),
                link = entry.linkHref(),
                publishedAt = parseDate(
                    entry.childText("published") ?: entry.childText("updated")
                ),
                summary = clean(summaryText ?: contentText),
                id = entry.childText("id"),
                imageUrl = images.thumbnail,
                fullImageUrl = images.full,
                categories = entry.categories(),
            )
        }.filter { it.title.isNotBlank() },
    )

    /**
     * Feeds are documents from strangers. Turning off external entities stops a
     * hostile feed from making the app read local files or fire off requests on
     * its behalf: a very old XML trick that still works on parsers left at
     * their defaults.
     */
    private fun documentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // These switches are best-effort: desktop Java supports them,
            // Android's parser throws on the Apache ones. Insisting on them
            // meant every feed failed to parse on a phone while working
            // perfectly in tests.
            for (feature in SECURITY_FEATURES) {
                runCatching { setFeature(feature.first, feature.second) }
            }
            runCatching { isXIncludeAware = false }
            isExpandEntityReferences = false
        }

        return factory.newDocumentBuilder().apply {
            // The portable defence, and the one that actually matters. Feeds
            // are documents from strangers; without this, a hostile feed can
            // make the app read local files or fire off requests on its behalf.
            // Returning an empty document for every external reference works
            // the same way on every platform.
            setEntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
        }
    }

    private val SECURITY_FEATURES = listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

    /**
     * Map of standard North American, European, Australasian, and Asian timezone codes
     * to numeric offsets, because Java's DateTimeFormatter rejects named abbreviations in RFC-1123.
     */
    private val TIMEZONE_OFFSETS = mapOf(
        // UTC / European
        "GMT" to "+0000",
        "UTC" to "+0000",
        "UT" to "+0000",
        "Z" to "+0000",
        "WET" to "+0000",
        "WEST" to "+0100",
        "BST" to "+0100",
        "CET" to "+0100",
        "CEST" to "+0200",
        "EET" to "+0200",
        "EEST" to "+0300",
        "MSK" to "+0300",
        // North American
        "EST" to "-0500",
        "EDT" to "-0400",
        "CST" to "-0600",
        "CDT" to "-0500",
        "MST" to "-0700",
        "MDT" to "-0600",
        "PST" to "-0800",
        "PDT" to "-0700",
        "AKST" to "-0900",
        "AKDT" to "-0800",
        "HST" to "-1000",
        "AST" to "-0400",
        "ADT" to "-0300",
        // Australasian
        "AEST" to "+1000",
        "AEDT" to "+1100",
        "ACST" to "+0930",
        "ACDT" to "+1030",
        "AWST" to "+0800",
        "NZST" to "+1200",
        "NZDT" to "+1300",
        // Asian & Middle Eastern
        "JST" to "+0900",
        "KST" to "+0900",
        "SGT" to "+0800",
        "HKT" to "+0800",
        "IST" to "+0530",
        "PKT" to "+0500",
        "WIB" to "+0700",
        "GST" to "+0400",
    )

    /**
     * Feed dates are notoriously inconsistent across real outlets.
     * Some omit the day of the week, some use single-digit days (e.g. 5 Sep),
     * some use 2-digit years, and many use named timezone abbreviations.
     */
    internal fun parseDate(raw: String?): Instant? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // 1. If text is a raw epoch timestamp (seconds or millis)
        if (text.all { it.isDigit() }) {
            val num = text.toLongOrNull()
            if (num != null) {
                return if (num > 100_000_000_000L) Instant.ofEpochMilli(num) else Instant.ofEpochSecond(num)
            }
        }

        // Fast path for ISO date-only format (e.g. Nature, Dublin Core dc:date "2026-09-18")
        if (text.length == 10 && text[4] == '-' && text[7] == '-') {
            val ld = runCatching { LocalDate.parse(text) }.getOrNull()
            if (ld != null) return ld.atStartOfDay(ZoneOffset.UTC).toInstant()
        }

        // 2. Normalize named timezone abbreviations if present at the end
        val normalized = normalizeTimezone(text)

        // 3. Try standard ISO formats first (fast path for Atom and ISO feeds)
        runCatching { return Instant.parse(normalized) }
        runCatching { return OffsetDateTime.parse(normalized).toInstant() }
        runCatching { return ZonedDateTime.parse(normalized).toInstant() }

        // 4. Try lenient RFC-822 / RFC-1123 parser
        for (formatter in FLEXIBLE_FORMATTERS) {
            runCatching {
                val accessor = formatter.parse(normalized)
                val zdt = runCatching { ZonedDateTime.from(accessor) }.getOrNull()
                if (zdt != null) return zdt.toInstant()

                val odt = runCatching { OffsetDateTime.from(accessor) }.getOrNull()
                if (odt != null) return odt.toInstant()

                val ldt = runCatching { LocalDateTime.from(accessor) }.getOrNull()
                if (ldt != null) return ldt.toInstant(ZoneOffset.UTC)

                val ld = runCatching { LocalDate.from(accessor) }.getOrNull()
                if (ld != null) return ld.atStartOfDay(ZoneOffset.UTC).toInstant()
            }
        }

        return null
    }

    private fun normalizeTimezone(raw: String): String {
        val lastSpace = raw.lastIndexOf(' ')
        if (lastSpace != -1 && lastSpace < raw.length - 1) {
            val tz = raw.substring(lastSpace + 1).uppercase(Locale.US)
            val offset = TIMEZONE_OFFSETS[tz]
            if (offset != null) {
                return raw.substring(0, lastSpace) + " " + offset
            }
        }
        return raw
    }

    private val FLEXIBLE_FORMATTERS = listOf(
        // RFC-822 / 1123 with optional day-of-week, 1-or-2 digit day, 2-or-4 digit year, optional seconds
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .optionalStart().appendPattern("EEE, ").optionalEnd()
            .optionalStart().appendPattern("E, ").optionalEnd()
            .appendPattern("d MMM ")
            .optionalStart().appendPattern("yyyy ").optionalEnd()
            .optionalStart().appendPattern("yy ").optionalEnd()
            .appendPattern("HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendPattern(" X").optionalEnd()
            .optionalStart().appendPattern(" Z").optionalEnd()
            .optionalStart().appendPattern(" z").optionalEnd()
            .toFormatter(Locale.US),

        // ISO-like: "2026-09-19", "2026-09-19 14:02:10", "2026-09-19T14:02:10"
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("yyyy-MM-dd")
            .optionalStart()
                .optionalStart().appendLiteral('T').optionalEnd()
                .optionalStart().appendLiteral(' ').optionalEnd()
                .appendPattern("HH:mm")
                .optionalStart().appendPattern(":ss").optionalEnd()
                .optionalStart().appendPattern(".SSS").optionalEnd()
                .optionalStart().appendPattern("X").optionalEnd()
                .optionalStart().appendPattern(" Z").optionalEnd()
            .optionalEnd()
            .toFormatter(Locale.US),
    )

    private val HTML_TAG_REGEX = Pattern.compile("<[^>]*>")
    private val WHITESPACE_REGEX = Pattern.compile("\\s+")
    private val IMG_SRC_REGEX = Pattern.compile("<img[^>]+src=[\"'](https?://[^\"'>]+)[\"']", Pattern.CASE_INSENSITIVE)
    private val RSS_BOILERPLATE = Regex(
        """(?i)\s*(?:the\s+post\b.*?(?:first\s+appeared\s+on|appeared\s+first\s+on)\b.*|\b(?:continue\s+reading|read\s+more|read\s+full\s+article)\b(?:\s*[.…]+)?|\bphoto\s+(?:by|credit)\b.*|\bcopyright\s+\d{4}\b.*|\ball\s+rights\s+reserved\b.*)\s*$"""
    )

    /**
     * Strips leftover HTML tags, decodes HTML entities, strips trailing boilerplate, and squashes whitespace.
     */
    internal fun clean(raw: String?): String? {
        val text = raw ?: return null
        val noTags = HTML_TAG_REGEX.matcher(text).replaceAll(" ")
        val decoded = decodeHtmlEntities(noTags)
        val withoutBoilerplate = RSS_BOILERPLATE.replace(decoded, "")
        val normalized = WHITESPACE_REGEX.matcher(withoutBoilerplate).replaceAll(" ").trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    /**
     * Comprehensive decoder for HTML named and numeric entities (e.g. &amp;, &#8217;),
     * delegating to the shared [com.medialiteracy.model.HtmlEntities] engine.
     */
    internal fun decodeHtmlEntities(input: String): String =
        com.medialiteracy.model.HtmlEntities.decode(input)

    /**
     * Extracts first image URL from raw HTML text if present.
     */
    internal fun extractImageFromHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        // If HTML contains encoded entities (e.g. &lt;img src=&quot;...&quot;&gt;), decode first
        val decoded = if (html.contains("&lt;") || html.contains("&quot;")) decodeHtmlEntities(html) else html
        val matcher = IMG_SRC_REGEX.matcher(decoded)
        return if (matcher.find()) matcher.group(1) else null
    }
}

// --- small helpers for walking the XML ------------------------------------

private fun String.equalsIgnoreCase(other: String) = equals(other, ignoreCase = true)

/** Direct children only: avoids grabbing a <title> from somewhere nested. */
private fun Element.child(name: String): Element? = children().firstOrNull {
    it.tagName.equalsIgnoreCase(name) || it.tagName.substringAfter(':').equalsIgnoreCase(name)
}

private fun Element.childText(name: String): String? =
    child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private fun Element.children(): List<Element> =
    (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) }
        .filter { it.nodeType == Node.ELEMENT_NODE }
        .map { it as Element }

private fun Element.descendants(name: String): List<Element> {
    val all = getElementsByTagName("*")
    return (0 until all.length)
        .mapNotNull { all.item(it) as? Element }
        .filter { it.tagName.equalsIgnoreCase(name) || it.tagName.substringAfter(':').equalsIgnoreCase(name) }
}

/**
 * Atom puts the address in an attribute, and usually offers several: the one
 * for humans is rel="alternate", which is also the default when rel is absent.
 */
private fun Element.linkHref(): String? {
    val links = children().filter { it.tagName.substringAfter(':').equalsIgnoreCase("link") }
    val alternate = links.firstOrNull {
        val rel = it.getAttribute("rel")
        rel.isNullOrEmpty() || rel == "alternate"
    }
    return (alternate ?: links.firstOrNull())?.getAttribute("href")?.takeIf { it.isNotEmpty() }
}

/** [ArticleImages.thumbnail] for the row icon, [ArticleImages.full] for a Card's hero picture. */
private data class ArticleImages(val thumbnail: String?, val full: String?)

/**
 * Preview pictures, checked across standard RSS/Atom and Media RSS (MRSS)
 * tags in priority order per industry specifications:
 * 1. Media RSS <media:thumbnail> (direct or in <media:group>)
 * 2. Media RSS <media:content> with medium="image" or image MIME type or image extension
 * 3. Enclosure with image MIME type or image extension
 * 4. iTunes <itunes:image>
 * 5. Fallback: <img> tag in HTML description or content (with entity decoding)
 *
 * [ArticleImages.thumbnail] is always the first of these found, exactly as
 * before this returned one URL - existing behaviour for the row icon is
 * unchanged. [ArticleImages.full] is the best *larger* picture the same feed
 * item offers: media:content or an enclosure, which sit behind a
 * media:thumbnail in the priority list above because they are usually the
 * full-size image the thumbnail was cropped from. Falls back to whatever
 * [ArticleImages.thumbnail] found when the feed only ever gave one size.
 */
private fun Element.images(descHtml: String? = null, contentHtml: String? = null): ArticleImages {
    val mediaGroup = children().firstOrNull { it.tagName.substringAfter(':').equalsIgnoreCase("group") }
    val candidateElements = if (mediaGroup != null) children() + mediaGroup.children() else children()

    // 1. Media RSS's dedicated thumbnail tag (<media:thumbnail url="...">): BBC and Daily Mail
    val thumbnailTag = candidateElements.firstOrNull { it.tagName.substringAfter(':').equalsIgnoreCase("thumbnail") }
        ?.getAttribute("url")?.takeIf { it.isNotEmpty() }

    // 2. Media RSS's <media:content>: NYT (medium="image"), Guardian (url with width), Wired, etc.
    val mediaContent = candidateElements.filter { it.tagName.substringAfter(':').equalsIgnoreCase("content") }
        .mapNotNull { elem ->
            val url = elem.getAttribute("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val type = elem.getAttribute("type")
            val medium = elem.getAttribute("medium")
            val isImage = type.startsWith("image/", ignoreCase = true) ||
                medium.equalsIgnoreCase("image") ||
                isImageUrl(url) ||
                (!medium.equalsIgnoreCase("audio") && !medium.equalsIgnoreCase("video") && !type.startsWith("audio/") && !type.startsWith("video/"))
            if (isImage) url else null
        }.firstOrNull()

    // 3. <enclosure>, RSS's general "attached file" tag
    val enclosure = candidateElements.firstOrNull { it.tagName.equalsIgnoreCase("enclosure") }
        ?.takeIf {
            val type = it.getAttribute("type")
            val url = it.getAttribute("url")
            type.startsWith("image/", ignoreCase = true) || isImageUrl(url)
        }
        ?.getAttribute("url")?.takeIf { it.isNotEmpty() }

    // 4. iTunes podcast / channel image: <itunes:image href="...">
    val itunesImage = candidateElements.firstOrNull { it.tagName.substringAfter(':').equalsIgnoreCase("image") }
        ?.getAttribute("href")?.takeIf { it.isNotEmpty() }

    // 5. Fallback: extract from description or content HTML
    val htmlImage by lazy {
        FeedParser.extractImageFromHtml(descHtml) ?: FeedParser.extractImageFromHtml(contentHtml)
    }

    val thumbnail = thumbnailTag ?: mediaContent ?: enclosure ?: itunesImage ?: htmlImage
    // The larger candidates first - they exist specifically to be bigger than
    // a thumbnail - then the same fallbacks a thumbnail-only feed would use.
    val full = mediaContent ?: enclosure ?: thumbnailTag ?: itunesImage ?: htmlImage

    return ArticleImages(thumbnail = thumbnail, full = full)
}

private fun isImageUrl(url: String): Boolean {
    val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
    return clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
        clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".avif")
}

private fun Element.categories(): List<String> =
    children()
        .filter { it.tagName.equalsIgnoreCase("category") || it.tagName.substringAfter(':').equalsIgnoreCase("category") }
        .mapNotNull { elem ->
            val term = elem.getAttribute("term").takeIf { it.isNotBlank() }
            val label = elem.getAttribute("label").takeIf { it.isNotBlank() }
            val text = elem.textContent?.trim()?.takeIf { it.isNotBlank() }
            FeedParser.clean(term ?: label ?: text)
        }
        .filter { it.isNotBlank() }

