package com.medialiteracy.benchmark

import java.io.File

object ToneSources {
    /** BABE (Spinde et al., 2021): semicolon CSV with expert sentence labels and biased-word lists. */
    fun readBabe(file: File): List<SentenceToneCase> {
        val rows = Delimited.parse(file.readText(Charsets.UTF_8), ';')
        val header = rows.first().map { it.trim() }
        val text = header.indexOf("text")
        val label = header.indexOf("label_bias")
        val words = header.indexOf("biased_words")
        val lean = header.indexOf("type")
        require(text >= 0 && label >= 0 && words >= 0) { "BABE header changed: $header" }
        return rows.drop(1).mapNotNull { row ->
            val sentence = row.getOrNull(text)?.trim().orEmpty()
            val toned = when (row.getOrNull(label)?.trim()) {
                "Biased" -> true
                "Non-biased" -> false
                else -> return@mapNotNull null
            }
            if (sentence.isEmpty()) return@mapNotNull null
            SentenceToneCase(
                sha256Hex(sentence), sentence, toned, parseWordList(row.getOrNull(words).orEmpty()),
                group = row.getOrNull(lean)?.trim()?.ifEmpty { null },
            )
        }
    }

    /** CheckThat! 2024 Task 2 English gold: `sentence_id<TAB>sentence<TAB>SUBJ|OBJ`. */
    fun readCheckThat(file: File): List<SentenceToneCase> =
        file.readLines(Charsets.UTF_8).drop(1).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) return@mapNotNull null
            val toned = when (p[2].trim()) {
                "SUBJ" -> true
                "OBJ" -> false
                else -> return@mapNotNull null
            }
            SentenceToneCase(p[0].trim(), p[1].trim(), toned)
        }

    private val LIST_ITEM = Regex("""'([^']*)'|"([^"]*)"""")

    /** Python list literals as BABE stores them: `['bizarre', "don't"]`. */
    fun parseWordList(field: String): List<String> =
        LIST_ITEM.findAll(field).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter { it.isNotBlank() }.toList()
}
