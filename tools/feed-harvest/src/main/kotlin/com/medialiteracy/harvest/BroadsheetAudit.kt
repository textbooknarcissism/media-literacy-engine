package com.medialiteracy.harvest

import com.medialiteracy.feeds.FeedParser
import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.flagging.SummaryContextResolver
import com.medialiteracy.model.FeedItem
import com.medialiteracy.model.FlagResult
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun main() {
    val sections = mapOf(
        "UK News" to "https://www.theguardian.com/uk/rss",
        "Comment is Free (Opinion)" to "https://www.theguardian.com/commentisfree/rss",
        "UK Politics" to "https://www.theguardian.com/politics/rss",
        "Culture" to "https://www.theguardian.com/culture/rss",
        "Environment" to "https://www.theguardian.com/uk/environment/rss",
        "World News" to "https://www.theguardian.com/world/rss",
    )

    println("==================================================")
    println("  Media Literacy Engine: Broadsheet Audit Tool   ")
    println("==================================================")

    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
    val allItems = mutableListOf<Pair<String, FeedItem>>()

    for ((sectionName, url) in sections) {
        print("Fetching $sectionName... ")
        try {
            val req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val parsed = FeedParser.parse(resp.body())
                println("ok (${parsed.items.size} articles)")
                for (item in parsed.items) {
                    allItems.add(sectionName to item)
                }
            } else {
                println("HTTP ${resp.statusCode()}")
            }
        } catch (e: Exception) {
            println("error: ${e.message}")
        }
    }

    val uniqueItems = allItems.distinctBy { it.second.link ?: it.second.title }
    println("\nTotal Unique Articles Harvested: ${uniqueItems.size}\n")

    val flagged = mutableListOf<Triple<String, FeedItem, FlagResult>>()
    val unflagged = mutableListOf<Pair<String, FeedItem>>()

    var trailingNudgesCount = 0
    var walkBackCount = 0
    var attributionShiftCount = 0

    for ((section, item) in uniqueItems) {
        val res = flagger.flagWithSummary(item.title, item.summary)
        if (res.isFlagged) {
            flagged.add(Triple(section, item, res))
            if (res.hits.any { it.matchedText.contains("Should he", ignoreCase = true) || it.matchedText.contains("too late", ignoreCase = true) }) {
                trailingNudgesCount++
            }
            if (res.resolvedContext != null) {
                if (res.resolvedContext!!.contains("threat", ignoreCase = true) || res.resolvedContext!!.contains("risk", ignoreCase = true)) {
                    walkBackCount++
                } else {
                    attributionShiftCount++
                }
            }
        } else {
            unflagged.add(section to item)
        }
    }

    println("==================================================")
    println("  AUDIT METRICS & FINDINGS")
    println("==================================================")
    val pct = if (uniqueItems.isNotEmpty()) (flagged.size * 100.0) / uniqueItems.size else 0.0
    println("  Total Flagged:          ${flagged.size} / ${uniqueItems.size} (${"%.1f".format(pct)}%)")
    println("  Trailing Debate Nudges: $trailingNudgesCount")
    println("  Context Resolutions:   ${walkBackCount + attributionShiftCount} ($walkBackCount walk-backs, $attributionShiftCount attributions/answers)")
    println()

    println("  BREAKDOWN BY SECTION:")
    for ((sectionName, _) in sections) {
        val sectionTotal = uniqueItems.count { it.first == sectionName }
        val sectionFlagged = flagged.count { it.first == sectionName }
        val sectionPct = if (sectionTotal > 0) (sectionFlagged * 100.0) / sectionTotal else 0.0
        println("    %-30s %3d / %3d (%4.1f%%)".format(sectionName, sectionFlagged, sectionTotal, sectionPct))
    }
    println()

    println("==================================================")
    println("  SAMPLE FLAGGED BROADSHEET ARTICLES")
    println("==================================================")
    for ((section, item, res) in flagged.take(15)) {
        println("  [$section] [${res.level}] [${res.categories.joinToString { it.displayName }}]")
        println("    ${item.title}")
        println("    Hits: ${res.hits.map { "${it.category.id}: '${it.matchedText}'" }}")
        if (res.resolvedContext != null) {
            println("    Context: \"${res.resolvedContext}\"")
        }
        println()
    }

    println("✓ Broadsheet audit complete.")
}
