package com.medialiteracy.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

object TeaseSources {
    /** Every post in the Webis-Clickbait-22 zip (train, validation and test files). */
    fun readWebis22(zip: File): List<TeaseCase> = ZipFile(zip).use { z ->
        z.entries().asSequence()
            .filter { it.name.endsWith(".jsonl") }
            .sortedBy { it.name }
            .flatMap { entry ->
                z.getInputStream(entry).bufferedReader(Charsets.UTF_8).readLines()
                    .filter { it.isNotBlank() }
                    .map(::parseWebisLine)
            }
            .toList()
    }

    /**
     * One JSON line. The article's own description stands in for the RSS
     * summary, because that is what the resolver sees in the reader app.
     */
    fun parseWebisLine(line: String): TeaseCase {
        val o = Json.parseToJsonElement(line).jsonObject
        fun strings(key: String) = (o[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return TeaseCase(
            id = o.getValue("uuid").jsonPrimitive.content,
            tease = strings("postText").joinToString(" ").trim(),
            summary = (o["targetDescription"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            spoilers = strings("spoiler"),
        )
    }
}
