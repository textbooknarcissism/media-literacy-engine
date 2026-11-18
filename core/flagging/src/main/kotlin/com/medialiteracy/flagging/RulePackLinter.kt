package com.medialiteracy.flagging

/**
 * Static analyzer for RulePack data files.
 *
 * Verifies:
 * 1. Signal References: All signals referenced in rule packs exist in [Signals.registeredSignalIds].
 * 2. Term Hygiene: No blank strings, no duplicate terms across tiers within a category.
 * 3. Threshold Integrity: subtle > 0, prominent > subtle, category weights > 0.
 */
object RulePackLinter {

    enum class Severity { ERROR, WARNING }

    data class LintIssue(
        val categoryId: String?,
        val severity: Severity,
        val message: String,
    )

    data class LintReport(
        val issues: List<LintIssue>,
    ) {
        val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
        val errors: List<LintIssue> get() = issues.filter { it.severity == Severity.ERROR }
        val warnings: List<LintIssue> get() = issues.filter { it.severity == Severity.WARNING }

        fun format(): String = buildString {
            if (issues.isEmpty()) {
                appendLine("✓ RulePack lint clean: 0 issues found.")
            } else {
                appendLine("RulePack Lint Report (${errors.size} error(s), ${warnings.size} warning(s)):")
                for (issue in issues) {
                    val prefix = if (issue.severity == Severity.ERROR) "[ERROR]" else "[WARN]"
                    val cat = issue.categoryId?.let { " ($it)" } ?: ""
                    appendLine("  $prefix$cat: ${issue.message}")
                }
            }
        }
    }

    fun lint(pack: RulePack): LintReport {
        val issues = mutableListOf<LintIssue>()
        val registeredSignals = Signals.registeredSignalIds

        // 1. Thresholds
        if (pack.thresholds.subtle <= 0.0) {
            issues += LintIssue(null, Severity.ERROR, "subtle threshold must be > 0.0 (got ${pack.thresholds.subtle})")
        }
        if (pack.thresholds.prominent <= pack.thresholds.subtle) {
            issues += LintIssue(
                null,
                Severity.ERROR,
                "prominent threshold (${pack.thresholds.prominent}) must be > subtle (${pack.thresholds.subtle})",
            )
        }

        // 2. Categories
        for (cat in pack.categories) {
            if (cat.weight <= 0.0) {
                issues += LintIssue(cat.id, Severity.ERROR, "Category weight must be > 0.0 (got ${cat.weight})")
            }

            // Signal references
            val allSignals = cat.signals + cat.noticeSignals + cat.softSignals + cat.cancelSignals + cat.muteSignals
            for (sig in allSignals) {
                if (sig !in registeredSignals) {
                    issues += LintIssue(cat.id, Severity.ERROR, "Unregistered signal '$sig' referenced in category '${cat.id}'")
                }
            }

            // Rule-specific questions must point at a rule this category has
            val firingSignals = (cat.signals + cat.noticeSignals + cat.softSignals).toSet()
            val firingTerms = (cat.terms + cat.noticeTerms + cat.softTerms).map { it.lowercase() }.toSet()
            for ((key, question) in cat.questions) {
                val known = when {
                    key.startsWith("signal:") -> key.removePrefix("signal:") in firingSignals
                    key.startsWith("term:") -> key.removePrefix("term:").lowercase() in firingTerms
                    key.startsWith("compound:") -> cat.compound != null && key == "compound:${cat.id}"
                    else -> false
                }
                if (!known) {
                    issues += LintIssue(cat.id, Severity.ERROR, "Question keyed '$key' names no rule in category '${cat.id}'")
                }
                if (question.isBlank()) {
                    issues += LintIssue(cat.id, Severity.ERROR, "Blank question for '$key'")
                } else if (!question.trimEnd().endsWith("?")) {
                    issues += LintIssue(cat.id, Severity.WARNING, "Question for '$key' does not end with a question mark")
                }
            }

            // Blank check
            val allTerms = listOf(
                "terms" to cat.terms,
                "noticeTerms" to cat.noticeTerms,
                "softTerms" to cat.softTerms,
                "cancelIfPresent" to cat.cancelIfPresent,
            )
            for ((listName, list) in allTerms) {
                for (term in list) {
                    if (term.isBlank()) {
                        issues += LintIssue(cat.id, Severity.ERROR, "Empty or blank term found in '$listName'")
                    }
                }
            }

            // Intra-list duplicates
            for ((listName, list) in allTerms) {
                val seen = mutableSetOf<String>()
                for (term in list) {
                    val lower = term.lowercase()
                    if (!seen.add(lower)) {
                        issues += LintIssue(cat.id, Severity.WARNING, "Duplicate term '$term' within '$listName'")
                    }
                }
            }

            // Inter-tier duplicates (term exists in multiple tiers)
            val fullTermsLower = cat.terms.map { it.lowercase() }.toSet()
            val noticeTermsLower = cat.noticeTerms.map { it.lowercase() }.toSet()
            val softTermsLower = cat.softTerms.map { it.lowercase() }.toSet()

            val fullAndNotice = fullTermsLower intersect noticeTermsLower
            if (fullAndNotice.isNotEmpty()) {
                issues += LintIssue(cat.id, Severity.ERROR, "Terms present in both full terms and noticeTerms: $fullAndNotice")
            }

            val fullAndSoft = fullTermsLower intersect softTermsLower
            if (fullAndSoft.isNotEmpty()) {
                issues += LintIssue(cat.id, Severity.ERROR, "Terms present in both full terms and softTerms: $fullAndSoft")
            }

            val noticeAndSoft = noticeTermsLower intersect softTermsLower
            if (noticeAndSoft.isNotEmpty()) {
                issues += LintIssue(cat.id, Severity.ERROR, "Terms present in both noticeTerms and softTerms: $noticeAndSoft")
            }
        }

        return LintReport(issues)
    }

    /**
     * Looks for rule phrases copied out of test headlines instead of
     * describing a technique. The v20 rule pack had several: "recipe for
     * curtain-twitching disaster" matched one Guardian headline and nothing
     * else, so it raised the score on that sample while catching no other
     * wording of the same rhetoric.
     *
     * - **Error:** the phrase matches [freshHeldout] headlines and nothing in
     *   [otherHeadlines]. It was almost certainly lifted from held-out data.
     *   Phrases in [grandfathered] (already in the pack when the baseline was
     *   written) are exempt from this error.
     * - **Warning:** a phrase of three or more words that matches exactly one
     *   headline across everything given.
     */
    fun lintMemorisation(
        pack: RulePack,
        freshHeldout: List<String>,
        otherHeadlines: List<String>,
        grandfathered: Set<String> = emptySet(),
    ): LintReport {
        val heldout = freshHeldout.map(::normalizeHeadline)
        val others = otherHeadlines.map(::normalizeHeadline)
        val exempt = grandfathered.map { it.lowercase() }.toSet()
        val issues = mutableListOf<LintIssue>()
        for (cat in pack.categories) {
            for (term in (cat.terms + cat.noticeTerms + cat.softTerms).distinctBy { it.lowercase() }) {
                val regex = listOf(term).toAlternationRegex() ?: continue
                val heldoutHits = heldout.count { regex.containsMatchIn(it) }
                val otherHits = others.count { regex.containsMatchIn(it) }
                val words = term.trim().split(Regex("""\s+""")).size
                if (heldoutHits > 0 && otherHits == 0 && term.lowercase() !in exempt) {
                    issues += LintIssue(cat.id, Severity.ERROR,
                        "'$term' matches $heldoutHits held-out headline(s) and nothing else in the committed corpora; it looks copied from held-out data")
                } else if (words >= 3 && heldoutHits + otherHits == 1) {
                    issues += LintIssue(cat.id, Severity.WARNING,
                        "'$term' matches exactly one headline in the committed corpora; it looks memorized rather than general")
                }
            }
        }
        return LintReport(issues)
    }

    /**
     * The same check for signals. A signal is judged as a whole, and a signal
     * that is one regular expression is also judged alternative by
     * alternative, because an alternative lifted from one headline can hide
     * inside a signal that matches hundreds. v23 had one: a "why" pattern
     * whose alternatives were copied from individual harvested headlines.
     *
     * - **Error:** the signal or alternative matches [freshHeldout] headlines
     *   and nothing in [otherHeadlines], unless it is in [grandfathered]
     *   (a signal id, or "id: alternative") from when the baseline was written.
     * - **Warning:** a whole signal, or an alternative of three or more words,
     *   matches exactly one headline across everything given.
     */
    fun lintSignalMemorisation(
        pack: RulePack,
        freshHeldout: List<String>,
        otherHeadlines: List<String>,
        grandfathered: Set<String> = emptySet(),
    ): LintReport {
        val heldout = freshHeldout.map(::normalizeHeadline)
        val others = otherHeadlines.map(::normalizeHeadline)
        val allowlist = pack.allCapsAllowlist.toSet()
        val issues = mutableListOf<LintIssue>()
        for ((signal, categoryId) in signalsInUse(pack)) {
            for (unit in signalUnits(signal, allowlist)) {
                val heldoutHits = heldout.count(unit.matches)
                val otherHits = others.count(unit.matches)
                if (heldoutHits > 0 && otherHits == 0 && unit.label !in grandfathered) {
                    issues += LintIssue(categoryId, Severity.ERROR,
                        "signal '${unit.label}' matches $heldoutHits held-out headline(s) and nothing else in the committed corpora; it looks copied from held-out data")
                } else if (unit.words >= 3 && heldoutHits + otherHits == 1) {
                    issues += LintIssue(categoryId, Severity.WARNING,
                        "signal '${unit.label}' matches exactly one headline in the committed corpora; it looks memorized rather than general")
                }
            }
        }
        return LintReport(issues)
    }

    /** Every signal id and "id: alternative" the pack uses, for grandfathering in the baseline. */
    fun signalLabels(pack: RulePack): List<String> =
        signalsInUse(pack).flatMap { (signal, _) -> signalUnits(signal, emptySet()).map { it.label } }.distinct().sorted()

    private fun signalsInUse(pack: RulePack): List<Pair<String, String>> =
        pack.categories.flatMap { cat ->
            (cat.signals + cat.noticeSignals + cat.softSignals + cat.cancelSignals + cat.muteSignals).map { it to cat.id }
        }.distinctBy { it.first }

    private class SignalUnit(val label: String, val words: Int, val matches: (String) -> Boolean)

    private val PATTERN_WHITESPACE = Regex("""\\s|\s""")

    private fun signalUnits(signal: String, allowlist: Set<String>): List<SignalUnit> = buildList {
        add(SignalUnit(signal, Int.MAX_VALUE) { Signals.allMatches(signal, it, allowlist).isNotEmpty() })
        val regex = Signals.regexOf(signal) ?: return@buildList
        for ((alternative, pattern) in alternativesOf(regex.pattern).orEmpty()) {
            val altRegex = runCatching { Regex(pattern, regex.options) }.getOrNull() ?: continue
            val words = PATTERN_WHITESPACE.findAll(alternative).count() + 1
            add(SignalUnit("$signal: $alternative", words) { altRegex.containsMatchIn(it) })
        }
    }

    /**
     * The top-level alternatives of a pattern, each paired with a pattern that
     * keeps the text around it: "\b(?:a|b)\b" gives ("a", "\b(?:a)\b") and
     * ("b", "\b(?:b)\b"). Splits the pattern itself when it alternates at the
     * top, otherwise the outer group with the most alternatives. Null when
     * there is nothing to split.
     */
    internal fun alternativesOf(pattern: String): List<Pair<String, String>>? {
        class Group(val contentStart: Int, val lookaround: Boolean) {
            val bars = mutableListOf<Int>()
            var contentEnd = -1
        }
        val topBars = mutableListOf<Int>()
        val outer = mutableListOf<Group>()
        val open = ArrayDeque<Group>()
        var classDepth = 0
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i++
                classDepth > 0 -> when (c) {
                    '[' -> classDepth++
                    ']' -> classDepth--
                }
                c == '[' -> {
                    classDepth = 1
                    if (pattern.getOrNull(i + 1) == '^') i++
                    if (pattern.getOrNull(i + 1) == ']') i++
                }
                c == '(' -> {
                    val lookaround = listOf("(?=", "(?!", "(?<=", "(?<!").any { pattern.startsWith(it, i) }
                    val contentStart = when {
                        lookaround -> i + if (pattern.startsWith("(?<", i)) 4 else 3
                        pattern.startsWith("(?<", i) -> pattern.indexOf('>', i) + 1
                        pattern.startsWith("(?", i) -> pattern.indexOfAny(charArrayOf(':', ')'), i) + 1
                        else -> i + 1
                    }
                    open.addLast(Group(contentStart, lookaround))
                }
                c == ')' -> open.removeLastOrNull()?.let { group ->
                    group.contentEnd = i
                    if (open.isEmpty()) outer += group
                }
                c == '|' -> if (open.isEmpty()) topBars += i else open.last().bars += i
            }
            i++
        }
        if (topBars.isNotEmpty()) {
            return split(pattern, 0, pattern.length, topBars).map { (s, e) -> pattern.substring(s, e).let { it to it } }
        }
        val group = outer.filter { !it.lookaround && it.bars.isNotEmpty() }.maxByOrNull { it.bars.size } ?: return null
        val prefix = pattern.substring(0, group.contentStart)
        val suffix = pattern.substring(group.contentEnd)
        return split(pattern, group.contentStart, group.contentEnd, group.bars).map { (s, e) ->
            pattern.substring(s, e).let { it to prefix + it + suffix }
        }
    }

    private fun split(pattern: String, start: Int, end: Int, bars: List<Int>): List<Pair<Int, Int>> =
        (listOf(start - 1) + bars).zip(bars + end).map { (from, to) -> from + 1 to to }
}

