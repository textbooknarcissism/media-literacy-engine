package com.medialiteracy.flagging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulePackLintTest {

    @Test
    fun `default rulepack passes all static lint checks without errors`() {
        val pack = RulePackLoader.loadDefault()
        val report = RulePackLinter.lint(pack)

        println(report.format())
        assertFalse(
            report.hasErrors,
            "RulePack static lint failed with ${report.errors.size} error(s):\n${report.format()}"
        )
    }

    @Test
    fun `linter detects malformed thresholds and invalid signals`() {
        val badPack = RulePack(
            version = 999,
            thresholds = Thresholds(subtle = 0.80, prominent = 0.50), // prominent < subtle
            categories = listOf(
                CategoryRule(
                    id = "test_cat",
                    weight = 0.5,
                    signals = listOf("non_existent_signal_xyz"),
                    terms = listOf("test", "test"), // duplicate
                )
            ),
        )

        val report = RulePackLinter.lint(badPack)
        assertTrue(report.hasErrors)
        assertTrue(report.errors.any { it.message.contains("prominent threshold") })
        assertTrue(report.errors.any { it.message.contains("non_existent_signal_xyz") })
        assertTrue(report.warnings.any { it.message.contains("Duplicate term 'test'") })
    }

    private fun packWith(rule: CategoryRule) = RulePack(version = 1, thresholds = Thresholds(0.5, 1.0), categories = listOf(rule))

    @Test
    fun `memorisation lint errors on a phrase that only matches held-out headlines`() {
        val pack = packWith(CategoryRule(id = "opinion_as_fact", weight = 1.0, noticeTerms = listOf("recipe for curtain-twitching disaster", "is a disaster")))
        val report = RulePackLinter.lintMemorisation(
            pack,
            freshHeldout = listOf("Amazon's Ring wants to replace WhatsApp groups. It's a recipe for curtain-twitching disaster"),
            otherHeadlines = listOf("The new tax plan is a disaster for pensioners"),
        )
        assertEquals(1, report.errors.size)
        assertTrue(report.errors.single().message.contains("recipe for curtain-twitching disaster"))
    }

    @Test
    fun `memorisation lint warns on a long single-headline phrase and spares grandfathered terms`() {
        val pack = packWith(CategoryRule(id = "manufactured_fear", weight = 1.0, terms = listOf("digital pathway to hell")))

        val warned = RulePackLinter.lintMemorisation(pack, emptyList(), listOf("I watched Trump TV. It's a digital pathway to hell"))
        assertEquals(0, warned.errors.size)
        assertEquals(1, warned.warnings.size)

        val spared = RulePackLinter.lintMemorisation(pack, listOf("It's a digital pathway to hell"), emptyList(), grandfathered = setOf("digital pathway to hell"))
        assertEquals(0, spared.errors.size)
    }

    @Test
    fun `alternatives are split at the top level or in the largest outer group`() {
        assertEquals(listOf("a" to """\b(?:a)\b""", "b" to """\b(?:b)\b"""), RulePackLinter.alternativesOf("""\b(?:a|b)\b"""))
        assertEquals(listOf("x" to "x", "y\\s+z" to "y\\s+z"), RulePackLinter.alternativesOf("x|y\\s+z"))
        // Bars inside a character class, after a backslash or in a lookbehind do not split.
        assertEquals(null, RulePackLinter.alternativesOf("""[|a]b\|c"""))
        assertEquals(
            listOf("this" to """(?<![a|b])(?:this)\s""", "these" to """(?<![a|b])(?:these)\s"""),
            RulePackLinter.alternativesOf("""(?<![a|b])(?:this|these)\s"""),
        )
    }

    @Test
    fun `signal lint errors on an alternative that only matches held-out headlines`() {
        val pack = packWith(CategoryRule(id = "curiosity_gap", weight = 1.0, noticeSignals = listOf("teaser_prefix")))
        val heldout = listOf("Unmasking the man behind the pension scam")
        val others = listOf("Unmasked: the reality star behind the romance", "Unmasked: the councillor who never was")

        val report = RulePackLinter.lintSignalMemorisation(pack, heldout, others)
        assertEquals(1, report.errors.size, report.format())
        assertTrue(report.errors.single().message.contains("teaser_prefix: Unmasking"))

        val spared = RulePackLinter.lintSignalMemorisation(pack, heldout, others, grandfathered = setOf("teaser_prefix: Unmasking"))
        assertEquals(0, spared.errors.size)
    }

    @Test
    fun `signal lint warns when a whole signal matches a single headline`() {
        val pack = packWith(CategoryRule(id = "urgency_pressure", weight = 1.0, signals = listOf("just_in_label")))
        val report = RulePackLinter.lintSignalMemorisation(pack, emptyList(), listOf("Just In: Court reaches verdict in landmark case", "Court reaches verdict"))
        assertEquals(0, report.errors.size)
        assertTrue(report.warnings.any { it.message.contains("signal 'just_in_label'") }, report.format())
    }

    @Test
    fun `memorisation lint matches through curly quotes and dashes like the flagger does`() {
        val pack = packWith(CategoryRule(id = "manufactured_fear", weight = 1.0, terms = listOf("it's a digital pathway to hell")))
        val report = RulePackLinter.lintMemorisation(pack, listOf("I watched Trump TV – it’s a digital pathway to hell"), emptyList())
        assertEquals(1, report.errors.size, "The curly apostrophe must be normalised before matching, exactly as HeadlineFlagger does")
    }
}

