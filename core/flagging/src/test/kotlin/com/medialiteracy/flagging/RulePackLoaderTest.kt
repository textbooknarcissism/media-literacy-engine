package com.medialiteracy.flagging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RulePackLoaderTest {

    @Test
    fun `loadDefault returns valid built-in rulepack`() {
        val pack = RulePackLoader.loadDefault()
        assertEquals(RulePackLoader.CURRENT_SCHEMA_VERSION, pack.schemaVersion)
        assertTrue(pack.version >= 1, "Default rulepack revision must be >= 1")
        assertTrue(pack.categories.isNotEmpty(), "Default rulepack must contain categories")
        assertTrue(pack.thresholds.subtle > 0.0)
        assertTrue(pack.thresholds.prominent > pack.thresholds.subtle)
    }

    @Test
    fun `loadSafely returns Success for valid custom rulepack`() {
        val validJson = """
            {
                "version": 1,
                "thresholds": { "subtle": 0.5, "prominent": 1.0 },
                "categories": [
                    {
                        "id": "test_category",
                        "weight": 0.5,
                        "terms": ["shocking", "unbelievable"]
                    }
                ]
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(validJson)
        assertIs<RulePackLoadResult.Success>(result)
        assertEquals(1, result.pack.version)
        assertEquals(1, result.pack.schemaVersion)
        assertEquals(1, result.pack.categories.size)
        assertEquals("test_category", result.pack.categories.first().id)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `loadSafely falls back on malformed JSON`() {
        val brokenJson = "{ this is definitely not valid json }"

        val result = RulePackLoader.loadSafely(brokenJson)
        assertIs<RulePackLoadResult.Fallback>(result)
        assertEquals(RulePackLoader.loadDefault(), result.pack)
        assertTrue(result.reason.contains("Malformed rule file format"))
        assertNotNull(result.cause)
    }

    @Test
    fun `loadSafely falls back on future schema version`() {
        val futureJson = """
            {
                "version": 1,
                "schemaVersion": 2,
                "thresholds": { "subtle": 0.5, "prominent": 1.0 },
                "categories": []
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(futureJson)
        assertIs<RulePackLoadResult.Fallback>(result)
        assertEquals(RulePackLoader.loadDefault(), result.pack)
        assertTrue(result.reason.contains("newer than maximum supported schema"))
    }

    @Test
    fun `loadSafely falls back on older unsupported schema version`() {
        val ancientJson = """
            {
                "version": 1,
                "schemaVersion": 0,
                "thresholds": { "subtle": 0.5, "prominent": 1.0 },
                "categories": []
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(ancientJson)
        assertIs<RulePackLoadResult.Fallback>(result)
        assertEquals(RulePackLoader.loadDefault(), result.pack)
        assertTrue(result.reason.contains("older than minimum supported schema"))
    }

    @Test
    fun `loadSafely falls back on non-positive rule revision`() {
        val zeroVersionJson = """
            {
                "version": 0,
                "thresholds": { "subtle": 0.5, "prominent": 1.0 },
                "categories": []
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(zeroVersionJson)
        assertIs<RulePackLoadResult.Fallback>(result)
        assertEquals(RulePackLoader.loadDefault(), result.pack)
        assertTrue(result.reason.contains("revision version must be positive"))
    }

    @Test
    fun `loadSafely falls back when linter finds quality errors`() {
        // Invalid threshold: prominent <= subtle
        val invalidThresholdJson = """
            {
                "version": 1,
                "thresholds": { "subtle": 0.8, "prominent": 0.4 },
                "categories": [
                    {
                        "id": "bad_rules",
                        "weight": 0.5,
                        "terms": ["test"]
                    }
                ]
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(invalidThresholdJson)
        assertIs<RulePackLoadResult.Fallback>(result)
        assertEquals(RulePackLoader.loadDefault(), result.pack)
        assertTrue(result.reason.contains("RulePack failed quality validation"))
        assertTrue(result.reason.contains("prominent threshold (0.4) must be > subtle (0.8)"))
    }

    @Test
    fun `loadSafely falls back when rule references unregistered signal`() {
        val badSignalJson = """
            {
                "version": 1,
                "thresholds": { "subtle": 0.5, "prominent": 1.0 },
                "categories": [
                    {
                        "id": "bad_signal_cat",
                        "weight": 0.5,
                        "signals": ["NON_EXISTENT_SIGNAL_ABC_123"]
                    }
                ]
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(badSignalJson)
        assertIs<RulePackLoadResult.Fallback>(result)
        assertEquals(RulePackLoader.loadDefault(), result.pack)
        assertTrue(result.reason.contains("Unregistered signal 'NON_EXISTENT_SIGNAL_ABC_123'"))
    }

    @Test
    fun `loadSafely returns Success with warnings when non-critical issues exist`() {
        // Duplicate term inside the same category triggers a WARNING, not an ERROR
        val duplicateTermJson = """
            {
                "version": 1,
                "thresholds": { "subtle": 0.5, "prominent": 1.0 },
                "categories": [
                    {
                        "id": "warn_cat",
                        "weight": 0.5,
                        "terms": ["repeated", "repeated"]
                    }
                ]
            }
        """.trimIndent()

        val result = RulePackLoader.loadSafely(duplicateTermJson)
        assertIs<RulePackLoadResult.Success>(result)
        assertEquals(1, result.pack.categories.size)
        assertTrue(result.warnings.isNotEmpty(), "Expected lint warning for duplicate term")
        assertTrue(result.warnings.any { it.contains("Duplicate term 'repeated'") })
    }

    @Test
    fun `loadOrFallback returns active rules directly without throwing`() {
        val goodJson = """
            {
                "version": 1,
                "thresholds": { "subtle": 0.4, "prominent": 0.9 },
                "categories": []
            }
        """.trimIndent()
        val loaded = RulePackLoader.loadOrFallback(goodJson)
        assertEquals(0.4, loaded.thresholds.subtle)

        val badJson = "not json"
        val fallback = RulePackLoader.loadOrFallback(badJson)
        assertEquals(RulePackLoader.loadDefault(), fallback)
    }
}
