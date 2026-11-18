package com.medialiteracy.benchmark

import com.medialiteracy.feeds.PageMetadata
import java.io.File

/**
 * Caches article HTML locally and extracts page metadata.
 *
 * All fetched HTML stays in the git-ignored `.cache/articles/` directory.
 * Known bot-blocked domains are marked without making failing network requests.
 */
object ArticleCache {
    val CACHE_DIR = listOf(File(".cache/articles"), File("../../.cache/articles")).firstOrNull { it.exists() } ?: File(".cache/articles")

    /**
     * Outlets known to block automated HTTP requests:
     * Atlantic, Axios, New Statesman, MarketWatch, Daily Star, MEN, FT, Politico.
     */
    private val BLOCKED_DOMAINS = setOf(
        "theatlantic.com",
        "axios.com",
        "newstatesman.com",
        "marketwatch.com",
        "dailystar.co.uk",
        "manchestereveningnews.co.uk",
        "ft.com",
        "politico.com",
    )

    /** Matches the link's host, so "ft.com" catches www.ft.com but not microsoft.com. */
    fun isBotBlocked(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase() ?: return false
        return BLOCKED_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    data class CachedMetadata(
        val section: String = "",
        val pageType: String = "",
        val fetchStatus: String = "ok",
    )

    fun fetchAndExtract(
        id: String,
        url: String,
        cacheDir: File = CACHE_DIR,
        fetch: (String) -> String = Http::text,
    ): CachedMetadata {
        val file = File(cacheDir, "$id.html")
        if (file.exists()) {
            val html = try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
            val meta = PageMetadata.extract(html, url)
            return CachedMetadata(
                section = meta.section.orEmpty(),
                pageType = meta.pageType.orEmpty(),
                fetchStatus = "ok",
            )
        }

        if (isBotBlocked(url)) {
            return CachedMetadata(fetchStatus = "blocked")
        }

        return try {
            val html = fetch(url)
            cacheDir.mkdirs()
            file.writeText(html, Charsets.UTF_8)
            val meta = PageMetadata.extract(html, url)
            CachedMetadata(
                section = meta.section.orEmpty(),
                pageType = meta.pageType.orEmpty(),
                fetchStatus = "ok",
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val status = when {
                msg.contains("403") || msg.contains("401") -> "blocked"
                msg.contains("404") -> "not_found"
                else -> "failed"
            }
            CachedMetadata(fetchStatus = status)
        }
    }
}
