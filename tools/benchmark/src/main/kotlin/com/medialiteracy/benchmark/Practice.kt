package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.FlagLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Practice mode (tools/practice): the decided edge cases as a deck.
 *
 *   practice export     writes build/practice/cases.json
 *
 * Each case is one distinct headline from the `fixed` rows of edge-cases.tsv, run
 * through the engine the way EdgeCasesTest runs it. The answer is what the engine
 * returns today. `why` for a case comes from tools/practice/why.json, if present.
 */
object Practice {
    private const val DECK = "core/flagging/src/test/resources/edge-cases.tsv"
    private const val WHY = "tools/practice/why.json"
    private const val OUT = "build/practice/cases.json"

    private val json = Json { prettyPrint = true }

    fun run(args: List<String>): Int = when (args.firstOrNull()) {
        "export" -> export()
        else -> {
            System.err.println("Use: practice export")
            2
        }
    }

    private fun export(): Int {
        val deck = File(DECK)
        if (!deck.exists()) {
            System.err.println("practice export needs $DECK; run it from the repository root")
            return 2
        }
        val why: Map<String, String> = File(WHY).takeIf { it.exists() }
            ?.let { Json.parseToJsonElement(it.readText(Charsets.UTF_8)).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content } }
            .orEmpty()
        val pack = RulePackLoader.loadDefault()
        val flagger = HeadlineFlagger(pack)
        val rows = deck.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("case\t") }
            .map { it.split('\t') }
            .filter { it.getOrNull(1) == "fixed" }
        val cases = rows.groupBy { it[4] }.values.map { group ->
            val p = group.first()
            val summary = p.getOrNull(6)?.ifBlank { null }
            val got = flagger.flag(
                p[4],
                ArticleContext(
                    articleType = ArticleType.fromId(p.getOrNull(5)?.ifBlank { null }),
                    summary = summary,
                    storyText = p.getOrNull(7)?.ifBlank { null },
                ),
            )
            val hits = FlagReview.hitsOf(got).map { json.encodeToJsonElement(FlagReview.Hit.serializer(), it) }
            JsonObject(
                buildMap {
                    put("k", JsonPrimitive(p[0]))
                    put("headline", JsonPrimitive(p[4]))
                    summary?.let { put("summary", JsonPrimitive(it)) }
                    put("level", JsonPrimitive(got.level.name))
                    put("hits", JsonArray(hits))
                    why[p[0]]?.takeIf { it.isNotBlank() }?.let { put("why", JsonPrimitive(it)) }
                },
            )
        }
        val root = JsonObject(
            mapOf(
                "codebook" to JsonPrimitive(CODEBOOK_VERSION),
                "rulePack" to JsonPrimitive(pack.version),
                "cases" to JsonArray(cases),
            ),
        )
        val out = File(OUT).also { it.parentFile.mkdirs() }
        out.writeText(json.encodeToString(JsonObject.serializer(), root) + "\n", Charsets.UTF_8)
        val flagged = cases.count { it["level"]?.jsonPrimitive?.contentOrNull != FlagLevel.NONE.name }
        println("practice: ${cases.size} cases, $flagged flagged, ${cases.size - flagged} left alone -> ${out.path}")
        return 0
    }
}

fun practice(args: List<String>): Int = Practice.run(args)
