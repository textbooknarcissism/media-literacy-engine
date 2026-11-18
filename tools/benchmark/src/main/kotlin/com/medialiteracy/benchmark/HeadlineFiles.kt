package com.medialiteracy.benchmark

import com.medialiteracy.feeds.ArticleText
import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import java.io.File

object HeadlineFiles {

    /** Column order of broadsheet-2026.tsv. */
    val BROADSHEET_COLUMNS = listOf("level", "techniques", "id", "outlet", "articleType", "typeSource", "link", "headline", "summary")

    /** Column order of beats-2026.tsv. */
    val BEATS_COLUMNS = listOf("level", "techniques", "id", "beat", "outlet", "articleType", "typeSource", "link", "labeller", "unsure", "headline", "note")

    /** Column order of context-2026.tsv. */
    val CONTEXT_COLUMNS = listOf(
        "level", "techniques", "id", "beat", "outlet", "articleType", "typeSource", "link",
        "labeller", "unsure", "headline", "note", "summary", "pageSection", "pageType", "fetchStatus",
        "story_link", "story_facts", "story_suggestion",
    )

    /**
     * `LEVEL<TAB>headline[<TAB># source[<TAB>articleType[<TAB>typeSource]]]`:
     * harvested.tsv, starter.tsv and the Chakraborty file. The article type is
     * optional; "unknown" or nothing means the headline alone decides, and
     * typeSource says how the type was found ("feed", "web-lookup:<url>").
     */
    fun readLegacy(file: File): List<HeadlineCase> = dataLines(file).mapNotNull { line ->
        val parts = line.split('\t').map { it.trim() }
        if (parts.any { it == "REVIEW" }) return@mapNotNull null
        val level = parseLevel(parts[0]) ?: return@mapNotNull null
        val headline = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val outlet = parts.getOrNull(2)?.removePrefix("#")?.trim().orEmpty().ifEmpty { "unknown" }
        HeadlineCase(
            id = headlineCaseId(headline),
            headline = headline,
            expectedLevel = level,
            outlet = outlet,
            articleType = parts.getOrNull(3)?.takeIf { it.isNotEmpty() && it != "unknown" },
            typeSource = parts.getOrNull(4)?.takeIf { it.isNotEmpty() },
        )
    }

    fun readBroadsheet(file: File): List<HeadlineCase> = dataLines(file).mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < BROADSHEET_COLUMNS.size) return@mapNotNull null
        val level = parseLevel(p[0].trim()) ?: return@mapNotNull null
        HeadlineCase(
            id = p[2].trim(),
            headline = p[7].trim(),
            expectedLevel = level,
            outlet = p[3].trim(),
            expectedTechniques = parseTechniques(p[1]),
            articleType = p[4].trim(),
            typeSource = p[5].trim(),
            link = p[6].trim(),
            summary = p[8].trim().ifEmpty { null },
        )
    }

    /** beats-2026.tsv. The labeler, unsure and note columns are for the people reviewing the labels; scoring ignores them. */
    fun readBeats(file: File): List<HeadlineCase> = dataLines(file).mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < BEATS_COLUMNS.size - 1) return@mapNotNull null
        val level = parseLevel(p[0].trim()) ?: return@mapNotNull null
        HeadlineCase(
            id = p[2].trim(),
            headline = p[10].trim(),
            expectedLevel = level,
            outlet = p[4].trim(),
            expectedTechniques = parseTechniques(p[1]),
            articleType = p[5].trim().takeIf { it.isNotEmpty() && it != "unknown" },
            typeSource = p[6].trim().ifEmpty { null },
            link = p[7].trim().ifEmpty { null },
            beat = p[3].trim().ifEmpty { null },
        )
    }

    /** context-2026.tsv: incorporates summary, pageSection, pageType, and cached article body text. */
    fun readContext(file: File): List<HeadlineCase> = dataLines(file).mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 11) return@mapNotNull null
        val level = parseLevel(p[0].trim()) ?: return@mapNotNull null
        val id = p[2].trim()
        val storyText = File(ArticleCache.CACHE_DIR, "$id.html")
            .takeIf { it.exists() }
            ?.let { runCatching { ArticleText.extract(it.readText(Charsets.UTF_8)) }.getOrNull() }
        HeadlineCase(
            id = id,
            headline = p[10].trim(),
            expectedLevel = level,
            outlet = p[4].trim(),
            expectedTechniques = parseTechniques(p[1]),
            articleType = p[5].trim().takeIf { it.isNotEmpty() && it != "unknown" },
            typeSource = p[6].trim().ifEmpty { null },
            link = p[7].trim().ifEmpty { null },
            beat = p[3].trim().ifEmpty { null },
            summary = p.getOrNull(12)?.trim()?.ifEmpty { null },
            pageSection = p.getOrNull(13)?.trim()?.ifEmpty { null },
            pageType = p.getOrNull(14)?.trim()?.ifEmpty { null },
            storyText = storyText,
        )
    }

    /**
     * The owner-reported set: id, outlet, headline, app verdict, proposed level
     * and techniques, call, note, then the maintainer's own level and techniques,
     * which win where given.
     */
    fun readOwner(file: File): List<HeadlineCase> = dataLines(file).filterNot { it.startsWith("id\t") }.mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 8) return@mapNotNull null
        val level = parseLevel((p.getOrNull(8)?.takeIf { it.isNotBlank() } ?: p[4]).trim()) ?: return@mapNotNull null
        HeadlineCase(
            id = p[0].trim(),
            headline = p[2].trim(),
            expectedLevel = level,
            outlet = p[1].trim(),
            expectedTechniques = parseTechniques(p.getOrNull(9)?.takeIf { it.isNotBlank() } ?: p[5]),
        )
    }

    fun parseTechniques(field: String): Set<Category> =
        if (field.trim() == "-") emptySet()
        else field.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .map { Category.fromId(it) ?: throw IllegalArgumentException("Unknown technique id '$it'") }
            .toSet()

    fun formatTechniques(techniques: Set<Category>): String =
        if (techniques.isEmpty()) "-" else techniques.map { it.id }.sorted().joinToString(",")

    private fun parseLevel(text: String): FlagLevel? = runCatching { FlagLevel.valueOf(text) }.getOrNull()

    private fun dataLines(file: File): List<String> = file.readLines(Charsets.UTF_8)
        .map { it.removePrefix("\uFEFF") }
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
}

/** Text safe to put in one TSV cell: no tabs or line breaks. */
fun tsvSafe(text: String): String = text.replace(Regex("""[\t\r\n]+"""), " ").trim()
