package com.medialiteracy.flagging

import com.medialiteracy.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternQuestionTest {

    private val base = RulePackLoader.loadDefault()

    private fun packWith(questions: Map<String, String>): RulePack = base.copy(
        categories = base.categories.map {
            if (it.id == Category.RAGE.id) it.copy(questions = questions) else it
        },
    )

    @Test
    fun `a hit without its own question asks the technique's question`() {
        val hit = HeadlineFlagger(base).flag("Minister slams rival over housing plan").hits.single()
        assertEquals(Category.RAGE, hit.category)
        assertEquals(hit.category.askYourself, hit.askYourself)
    }

    @Test
    fun `a word's question applies to that listed word and no other`() {
        val flagger = HeadlineFlagger(packWith(mapOf("term:slams" to "Who slammed whom, and how?")))
        val slams = flagger.flag("Minister slams rival over housing plan").hits.single { it.category == Category.RAGE }
        assertEquals("Who slammed whom, and how?", slams.askYourself)

        val slammed = flagger.flag("Minister slammed rival over housing plan").hits.single { it.category == Category.RAGE }
        assertNull(slammed.question)
    }

    @Test
    fun `a detector's question applies to that detector only`() {
        val flagger = HeadlineFlagger(packWith(mapOf("signal:staged_exchange" to "Did they answer each other, or just speak?")))
        val exchange = flagger.flag("Lily Allen responds to David Harbour's comments about her album")
            .hits.single { it.category == Category.RAGE }
        assertEquals("Did they answer each other, or just speak?", exchange.askYourself)

        val other = flagger.flag("Minister slams rival over housing plan").hits.single { it.category == Category.RAGE }
        assertNull(other.question)
    }

    @Test
    fun `questions in the rule pack survive a round trip through the loader`() {
        val withQuestion = packWith(mapOf("term:slams" to "Who slammed whom?"))
        val json = kotlinx.serialization.json.Json.encodeToString(RulePack.serializer(), withQuestion)
        val loaded = RulePackLoader.loadSafely(json)
        assertTrue(loaded is RulePackLoadResult.Success, "rule pack with questions was rejected")
        assertEquals("Who slammed whom?", loaded.pack.categories.single { it.id == Category.RAGE.id }.questions["term:slams"])
    }

    @Test
    fun `the linter rejects a question that names no rule or is blank`() {
        val report = RulePackLinter.lint(packWith(mapOf("term:notaword" to "Who?", "signal:staged_exchange" to " ")))
        assertTrue(report.errors.any { "term:notaword" in it.message })
        assertTrue(report.errors.any { "Blank question" in it.message })
    }

    @Test
    fun `the linter warns about a question that is not a question`() {
        val report = RulePackLinter.lint(packWith(mapOf("term:slams" to "Who slammed whom.")))
        assertFalse(report.hasErrors)
        assertTrue(report.warnings.any { "question mark" in it.message })
    }
}
