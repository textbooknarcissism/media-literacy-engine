package com.medialiteracy.flagging

import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.FlagResult
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The maintainer's edge cases from the monthly flag review (resources/edge-cases.tsv):
 * headlines they reviewed, with the outcome they decided. A `fixed` case must
 * pass, so a later rule pack cannot quietly undo a decision; an `open` case is
 * a known gap, reported here and in the flag review until a rule closes it.
 */
class EdgeCasesTest {
    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    private data class Case(
        val id: String,
        val fixed: Boolean,
        val present: List<String>,
        val absent: List<String>,
        val headline: String,
        val context: ArticleContext,
        val note: String,
    )

    private val cases: List<Case> by lazy {
        val text = javaClass.getResource("/edge-cases.tsv")?.readText(Charsets.UTF_8) ?: fail("edge-cases.tsv is missing")
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("case\t") }.map { line ->
            val p = line.split('\t')
            fun list(s: String) = if (s == "-" || s.isBlank()) emptyList() else s.split(',').map { it.trim() }
            Case(
                id = p[0],
                fixed = p[1] == "fixed",
                present = list(p[2]),
                absent = list(p[3]),
                headline = p[4],
                context = ArticleContext(
                    articleType = ArticleType.fromId(p.getOrNull(5)?.ifBlank { null }),
                    summary = p.getOrNull(6)?.ifBlank { null },
                    storyText = p.getOrNull(7)?.ifBlank { null },
                ),
                note = p.getOrElse(8) { "" },
            )
        }
    }

    /** What is wrong with this result for this case, or an empty list. */
    private fun problems(case: Case, got: FlagResult): List<String> = buildList {
        for (want in case.present) {
            if (want == "any") {
                if (!got.isFlagged) add("should be flagged")
                continue
            }
            val technique = want.substringBefore('~')
            val words = want.substringAfter('~', "")
            val found = got.hits.any { it.category.id == technique && (words.isEmpty() || it.matchedText.contains(words, ignoreCase = true)) }
            if (!found) add("missing $want")
        }
        for (avoid in case.absent) {
            if (avoid == "all") {
                if (got.isFlagged) add("should not be flagged")
            } else if (got.hits.any { it.category.id == avoid }) add("should not name $avoid")
        }
    }

    @Test
    fun `the maintainer's decided edge cases hold`() {
        assertTrue(cases.size >= 30, "expected the October cases, found ${cases.size}")
        val broken = mutableListOf<String>()
        for (case in cases) {
            val got = flagger.flag(case.headline, case.context)
            val wrong = problems(case, got)
            val shown = got.hits.joinToString { "${it.category.id}:\"${it.matchedText}\"" }.ifEmpty { "not flagged" }
            when {
                case.fixed && wrong.isNotEmpty() -> broken += "${case.id} ${wrong.joinToString("; ")} (got $shown): ${case.headline}"
                !case.fixed && wrong.isEmpty() -> println("Edge case ${case.id} now passes; mark it fixed in edge-cases.tsv.")
                !case.fixed -> println("Edge case ${case.id} still open: ${wrong.joinToString("; ")}. ${case.note}")
            }
        }
        if (broken.isNotEmpty()) fail("Owner-decided edge cases broken:\n" + broken.joinToString("\n"))
    }
}
