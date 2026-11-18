package com.medialiteracy.flagging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The shapes of the rule file (rulepack-v1.json).
 *
 * The rules live in a data file rather than in code on purpose: they are the
 * part of the engine most likely to need frequent tweaking, and a data file can
 * eventually be updated without shipping a new binary release.
 */
@Serializable
data class RulePack(
    val version: Int,
    val schemaVersion: Int = 1,
    val thresholds: Thresholds,
    val categories: List<CategoryRule>,
    val allCapsAllowlist: List<String> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class Thresholds(
    /** At or above this score, show a quiet marker. */
    val subtle: Double,
    /** At or above this score, show the marker at full strength. */
    val prominent: Double,
)

@Serializable
data class CategoryRule(
    val id: String,
    /** How much one match in this category contributes to the score. */
    val weight: Double,
    val enabledByDefault: Boolean = true,
    /** Plain words and phrases. Matched whole-word and ignoring capitals. Counts at full [weight]. */
    val terms: List<String> = emptyList(),
    /** Loaded words that trigger a Pattern Notice ('Proceed with caution') on their own. */
    val noticeTerms: List<String> = emptyList(),
    /** What a [noticeTerms] match is worth. Defaults to 0.50 (subtle threshold). */
    val noticeWeight: Double = 0.50,
    /**
     * Words in this category that are also used completely literally in
     * ordinary news: "deadly crash", "bridge collapse", "Met Office warning".
     * They count for much less on their own, so a plain factual headline stays
     * silent, but they still push a headline over the line when combined with
     * something else.
     */
    val softTerms: List<String> = emptyList(),
    /** What a [softTerms] match is worth. */
    val softWeight: Double = 0.35,
    /** Built-in detectors that count at full weight. See [Signals]. */
    val signals: List<String> = emptyList(),
    /** Built-in detectors that count as a Pattern Notice ('Proceed with caution'), at [noticeWeight]. */
    val noticeSignals: List<String> = emptyList(),
    /** Built-in detectors that count only as a soft signal, at [softWeight]. */
    val softSignals: List<String> = emptyList(),
    /**
     * Phrases that switch this whole category off for a headline.
     *
     * Used by speculative framing: a headline that hedges AND states its own
     * limits ("may slow tumour growth in mice") is being honest with you, and
     * honesty must never be what gets a headline marked.
     */
    val cancelIfPresent: List<String> = emptyList(),
    /**
     * Like [cancelIfPresent], but a built-in detector (see [Signals]) rather
     * than a fixed phrase: switches this whole category off for the headline.
     *
     * Used by vague sourcing, and it is the right shape *for that category*.
     * Everything vague sourcing looks for ("claims", "reportedly", a
     * scare-quoted fragment) is about who said something. So "did the
     * headline name its source?" is a question about the whole headline, and
     * one named source answers it wherever it appears: in "Microsoft says AI
     * rival Anthropic could have 'disastrous impact' on humanity", the quoted
     * phrase is what Microsoft actually said.
     */
    val cancelSignals: List<String> = emptyList(),
    /**
     * Built-in detectors whose matched words this category has to ignore,
     * only those words, not the whole headline.
     *
     * Used by manufactured fear, where [cancelSignals] is the wrong shape.
     * Fear's words are the outlet's own choice, so a name somewhere in the
     * sentence excuses nothing: "DR PHILIPPA KAYE reveals ticking timebomb of
     * side effects" should still be marked, and switching off the category
     * wiped it. What a named source does excuse is the reporting verb attached
     * to it ("Lina Khan warns" is grammar, not a frightening word choice) so
     * that verb, and nothing else, goes quiet.
     *
     * The two fields exist because the two categories are asking different
     * questions. Measured: making vague sourcing use muting instead of
     * canceling cost four wrongly flagged headlines and gained nothing.
     */
    val muteSignals: List<String> = emptyList(),
    /**
     * Questions written for single rules, keyed by the rule that fires:
     * "signal:staged_exchange" for a detector, "term:slam" for a listed word
     * (any inflection of it matches). A hit from a rule with no entry here
     * falls back to the technique's own question ([Category.askYourself]).
     */
    val questions: Map<String, String> = emptyMap(),
    /** Only used by science overhype, which needs several things to line up. */
    val compound: CompoundRule? = null,
)

/**
 * A rule that fires only when a combination is present, not a single word.
 *
 * This exists because "could" on its own is meaningless: honest science
 * reporting hedges constantly and should. What is worth flagging is a hedge
 * plus a big claim plus no mention of what stage the work is at. That last part
 * is why [cancelIfPresent] matters: a headline that already says "in lab tests"
 * has been honest with you, so it must not be flagged.
 */
@Serializable
data class CompoundRule(
    val hedges: List<String>,
    val claims: List<String>,
    @SerialName("claimSignals") val claimSignals: List<String> = emptyList(),
    val cancelIfPresent: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

sealed interface RulePackLoadResult {
    /** The active rules to use (either the loaded rules or safe defaults). */
    val pack: RulePack

    /**
     * Indicates that the rulepack loaded successfully and passed all quality checks.
     */
    data class Success(
        override val pack: RulePack,
        val warnings: List<String> = emptyList(),
    ) : RulePackLoadResult

    /**
     * Indicates that the provided rulepack was invalid, incompatible, or corrupted.
     * The loader safely fell back to the built-in [RulePackLoader.loadDefault].
     */
    data class Fallback(
        override val pack: RulePack,
        val reason: String,
        val cause: Throwable? = null,
    ) : RulePackLoadResult
}

object RulePackLoader {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    private val defaultPack by lazy {
        load(
            requireNotNull(RulePackLoader::class.java.getResourceAsStream("/rulepack-v1.json")) {
                "rulepack-v1.json is missing from the build"
            }.bufferedReader(Charsets.UTF_8).readText()
        )
    }

    /** Loads the rule file bundled inside the engine artifacts. Always valid and cached. */
    fun loadDefault(): RulePack = defaultPack

    /**
     * Strictly decodes a RulePack from a JSON string.
     * Throws an exception if the text is invalid JSON or cannot be deserialized.
     */
    fun load(text: String): RulePack = json.decodeFromString(RulePack.serializer(), text)

    /**
     * Safely loads a RulePack from an external JSON string (e.g. downloaded over the network).
     *
     * Performs safety checks:
     * 1. Valid JSON format.
     * 2. Supported schema layout version ([MIN_SUPPORTED_SCHEMA_VERSION]..[MAX_SUPPORTED_SCHEMA_VERSION]).
     * 3. Valid rule revision (must be positive).
     * 4. Static quality validation via [RulePackLinter] (thresholds, signals, term hygiene).
     *
     * If any check fails, returns [RulePackLoadResult.Fallback] with [loadDefault] and a clear reason.
     * Never throws exceptions to the caller.
     */
    fun loadSafely(text: String): RulePackLoadResult {
        val parsed = try {
            json.decodeFromString(RulePack.serializer(), text)
        } catch (e: Exception) {
            return RulePackLoadResult.Fallback(
                pack = loadDefault(),
                reason = "Malformed rule file format: ${e.message ?: "unknown parse error"}",
                cause = e,
            )
        }

        if (parsed.schemaVersion < MIN_SUPPORTED_SCHEMA_VERSION) {
            return RulePackLoadResult.Fallback(
                pack = loadDefault(),
                reason = "RulePack schema version ${parsed.schemaVersion} is older than minimum supported schema ($MIN_SUPPORTED_SCHEMA_VERSION).",
            )
        }

        if (parsed.schemaVersion > MAX_SUPPORTED_SCHEMA_VERSION) {
            return RulePackLoadResult.Fallback(
                pack = loadDefault(),
                reason = "RulePack schema version ${parsed.schemaVersion} is newer than maximum supported schema ($MAX_SUPPORTED_SCHEMA_VERSION). Update the engine library to use these rules.",
            )
        }

        if (parsed.version <= 0) {
            return RulePackLoadResult.Fallback(
                pack = loadDefault(),
                reason = "RulePack revision version must be positive (got ${parsed.version}).",
            )
        }

        val lintReport = RulePackLinter.lint(parsed)
        if (lintReport.hasErrors) {
            val errorSummary = lintReport.errors.joinToString("; ") { it.message }
            return RulePackLoadResult.Fallback(
                pack = loadDefault(),
                reason = "RulePack failed quality validation: $errorSummary",
            )
        }

        val warnings = lintReport.warnings.map { it.message }
        return RulePackLoadResult.Success(pack = parsed, warnings = warnings)
    }

    /**
     * Convenience method to load a RulePack safely, directly returning the active rules.
     * If loading fails, returns [loadDefault].
     */
    fun loadOrFallback(text: String): RulePack = loadSafely(text).pack
}

