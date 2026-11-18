package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Writes every labeled headline, with the rules' verdict, as JSON lines. Run
 * it before and after a rule change and diff the verdicts to see exactly which
 * headlines the change moved, held-out rows included (CONTRIBUTING.md,
 * "Development"). It once fed the model
 * bake-off, whose harness has been removed.
 *
 *   ./gradlew :tools:benchmark:run --args="export-cases [--out build/ml/cases.jsonl]"
 *
 * Each row carries `split`: TUNE or HELDOUT from [Split], or OWNER for the
 * owner-reported headlines, which are tuning-class data scored as their own set.
 */
object ExportCases {
    const val OWNER_FILE = "fixtures/headlines/owner-reported-2026-10.tsv"

    fun run(out: File, ownerFile: File = File(OWNER_FILE), datasets: List<HeadlineDataset> = HEADLINE_DATASETS): Int {
        val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
        out.parentFile?.mkdirs()
        var count = 0
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            for (ds in datasets) {
                val file = File(ds.path)
                // The reported set is written once below, as OWNER rows. A sealed set
                // never leaves the benchmark, not even as held-out rows.
                if (!file.exists() || ds.format == HeadlineFormat.OWNER || ds.isSealed()) continue
                for (case in ds.read(file)) {
                    w.write(line(ds.id, ds.partOf(case.id).name, case, flagger).toString())
                    w.newLine()
                    count++
                }
            }
            for (case in if (ownerFile.exists()) HeadlineFiles.readOwner(ownerFile) else emptyList()) {
                w.write(line("owner-reported", "OWNER", case, flagger).toString())
                w.newLine()
                count++
            }
        }
        return count
    }

    private fun line(dataset: String, split: String, case: HeadlineCase, flagger: HeadlineFlagger) = buildJsonObject {
        val type = ArticleType.fromId(case.articleType)
        // The benchmark's default view: headline, type, summary and page metadata.
        val rules = flagger.flag(
            case.headline,
            ArticleContext(articleType = type, summary = case.summary, pageSection = case.pageSection, pageType = case.pageType),
        )
        val inspected = flagger.inspect(case.headline, ArticleContext(articleType = type))
        put("dataset", dataset)
        put("id", case.id)
        put("split", split)
        put("headline", case.headline)
        put("outlet", case.outlet)
        put("articleType", case.articleType?.let { JsonPrimitive(it) } ?: JsonNull)
        put("summary", case.summary?.let { JsonPrimitive(it) } ?: JsonNull)
        put("expectedLevel", case.expectedLevel.name)
        put("expectedTechniques", case.expectedTechniques?.let { set -> JsonArray(set.map { JsonPrimitive(it.id) }) } ?: JsonNull)
        put("rulesLevel", rules.level.name)
        put("rulesScore", rules.score)
        put("rulesTechniques", JsonArray(rules.categories.map { JsonPrimitive(it.id) }))
        put("inspectLevel", inspected.level.name)
        put("inspectScore", inspected.score)
        put("inspectHits", hits(inspected))
    }

    private fun hits(r: FlagResult) = buildJsonArray {
        r.hits.forEach { h ->
            add(buildJsonObject {
                put("technique", h.category.id)
                put("text", h.matchedText)
                put("ruleId", h.ruleId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("weight", h.weight)
            })
        }
    }
}

fun exportCases(args: List<String>): Int {
    val out = File(parseFlags(args)["--out"] ?: "build/ml/cases.jsonl")
    val n = ExportCases.run(out)
    println("Wrote $n cases to ${out.path}")
    return 0
}
