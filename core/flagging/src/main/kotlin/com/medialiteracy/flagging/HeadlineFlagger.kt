package com.medialiteracy.flagging

import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import com.medialiteracy.model.PatternHit

/**
 * Checks a headline against the rule pack and decides whether (and how
 * strongly) to mark it.
 *
 * Create one of these and reuse it. All the pattern matching is prepared once
 * up front, because this runs against every headline in a feed and needs to be
 * fast enough that nobody ever notices it happening.
 *
 * Nothing in here touches the network, the AI, or the article body. It reads a
 * string and returns a verdict. That is the whole job.
 */
class HeadlineFlagger(
    private val pack: RulePack,
    /** Which techniques the user currently wants detected. Null means the rule file's defaults. */
    enabledCategories: Set<Category>? = null,
    /** Specific headlines the user muted locally on this device. */
    private val mutedHeadlines: Set<String> = emptySet(),
    /** Phrases muted globally across all feeds. */
    mutedPhrases: Set<String> = emptySet(),
    /** Phrases muted per outlet (sourceName in lowercase -> set of phrases). */
    private val mutedOutletPhrases: Map<String, Set<String>> = emptyMap(),
) {

    private val allCapsAllowlist: Set<String> = pack.allCapsAllowlist.toSet()
    private val globalMutedRegex: Regex? = mutedPhrases.filter { it.isNotBlank() }.toAlternationRegex()
    private val outletMutedRegexes: Map<String, Regex> = mutedOutletPhrases
        .mapKeys { it.key.trim().lowercase() }
        .mapNotNull { (outlet, phrases) ->
            phrases.filter { it.isNotBlank() }.toAlternationRegex()?.let { outlet to it }
        }.toMap()

    private val compiled: List<CompiledCategory> = pack.categories.mapNotNull { rule ->
        val category = Category.fromId(rule.id) ?: return@mapNotNull null
        val wanted = enabledCategories?.contains(category) ?: rule.enabledByDefault
        if (!wanted) return@mapNotNull null

        CompiledCategory(
            category = category,
            weight = rule.weight,
            noticeWeight = rule.noticeWeight,
            softWeight = rule.softWeight,
            softPairCap = pack.thresholds.subtle - SOFT_PAIR_MARGIN,
            terms = rule.terms.toAlternationRegex(),
            noticeTerms = rule.noticeTerms.toAlternationRegex(),
            softTerms = rule.softTerms.toAlternationRegex(),
            signals = rule.signals,
            noticeSignals = rule.noticeSignals,
            softSignals = rule.softSignals,
            cancels = rule.cancelIfPresent.toAlternationRegex(),
            cancelSignals = rule.cancelSignals,
            muteSignals = rule.muteSignals,
            questions = rule.questions.filterKeys { !it.startsWith("term:") },
            termQuestions = rule.questions
                .filterKeys { it.startsWith("term:") }
                .mapNotNull { (key, question) -> listOf(key.removePrefix("term:")).toAlternationRegex()?.let { it to question } },
            compound = rule.compound?.let { c ->
                CompiledCompound(
                    hedges = c.hedges.toAlternationRegex(),
                    claims = c.claims.toAlternationRegex(),
                    claimSignals = c.claimSignals,
                    cancels = c.cancelIfPresent.toAlternationRegex(),
                    domains = c.domains.toAlternationRegex(),
                )
            },
        )
    }

    /**
     * Checks a headline with what the caller knows about its article: its type,
     * page section and schema type, summary and story text.
     *
     * Pure and offline. Context settles facts: whether the piece is a signed
     * view (codebook rule 2), and what a tease withheld. The only flags it can
     * add are the two headline-body conflicts SummaryContextResolver already
     * knows: a buried lab-stage caveat and an official walk-back. The summary is
     * read first, then the opening [STORY_CHARS] characters of the story.
     */
    fun flag(
        headline: String,
        context: ArticleContext,
        sourceName: String? = null,
    ): FlagResult {
        if (headline.isBlank()) return FlagResult.NOT_FLAGGED
        if (headline in mutedHeadlines) return FlagResult.NOT_FLAGGED

        val inspected = inspect(headline, context, sourceName)

        // Below the quiet threshold nothing is reported at all, not a weak flag,
        // not a greyed-out one. Showing a marker for a single "could" is how
        // this feature would lose people's trust.
        val rawResult = if (inspected.level != FlagLevel.NONE) withoutRiders(inspected) else FlagResult(FlagLevel.NONE, inspected.score, emptyList())

        // The opening of a story carries what happened and its caveats; reading
        // the whole page would let a stray "in mice" far below add a flag.
        val afterSummary = SummaryContextResolver.resolve(headline, context.summary, rawResult)
        return SummaryContextResolver.resolve(headline, context.storyText?.take(STORY_CHARS), afterSummary)
    }

    /**
     * A flagged headline, told to the reader through the hits that could flag
     * it on their own: one clear technique, one question. A hit too weak to
     * flag a headline alone (a lone "could", a plain quoted fragment, a soft
     * fear word) used to ride along beside a real one and be shown as a second
     * technique. On tuning rows that second label matched the human label 5
     * times in 43, against 176 in 188 for a full hit, so it taught the wrong
     * thing more often than not. Two techniques are still shown when each is
     * strong enough alone. The level is worked out again from what is shown,
     * so one technique plus riders is SUBTLE, not PROMINENT. [inspect] still
     * returns every hit, for tools.
     */
    private fun withoutRiders(result: FlagResult): FlagResult {
        val shown = result.hits.filter { (it.weight ?: 0.0) >= pack.thresholds.subtle }
        if (shown.size == result.hits.size || shown.isEmpty()) return result
        val score = shown.sumOf { it.weight ?: 0.0 }
        val level = if (score >= pack.thresholds.prominent) FlagLevel.PROMINENT else FlagLevel.SUBTLE
        return FlagResult(level, score, shown)
    }

    /**
     * The headline's own verdict with every hit kept, including the ones that
     * scored below the quiet threshold. [flag] hides those from the reader;
     * this is for tools that study them (the benchmark export, and a model
     * second opinion that may promote a soft hit). The headline's words decide;
     * the summary and the story's opening only settle facts that remove a hit
     * (codebook §1), such as who did a study.
     */
    fun inspect(
        headline: String,
        context: ArticleContext = ArticleContext(),
        sourceName: String? = null,
    ): FlagResult {
        if (headline.isBlank()) return FlagResult.NOT_FLAGGED
        if (headline in mutedHeadlines) return FlagResult.NOT_FLAGGED

        val normalized = normalizeHeadline(headline)

        val userMutedRanges = mutableListOf<IntRange>()
        globalMutedRegex?.findAll(normalized)?.forEach { userMutedRanges.add(it.range) }
        if (sourceName != null) {
            outletMutedRegexes[sourceName.trim().lowercase()]?.findAll(normalized)?.forEach { userMutedRanges.add(it.range) }
        }

        val signalContext = Signals.SignalContext(allCapsAllowlist, context.resolvedArticleType, context.summary, context.storyText?.take(STORY_CHARS))
        val hits = mutableListOf<PatternHit>()
        val claimed = mutableListOf<IntRange>()
        var score = 0.0
        var hasHardOrNotice = false

        // Collect every category's best match first, then settle the arguments
        // between them. Two categories often match the same words: "experts
        // warn" is vague sourcing, and the "warn" inside it also looks like
        // fear. That is one technique, not two, and counting it twice is how a
        // mild headline ends up shouted at.
        //
        // A full-strength match wins over a quiet-notice one, then the longer
        // and more specific phrase, then the heavier weight. So "experts warn"
        // beats "warn".
        val candidates = compiled
            .mapNotNull { rule -> rule.findIn(normalized, signalContext, userMutedRanges)?.let { rule to it } }
            .sortedWith(
                compareByDescending<Pair<CompiledCategory, Found>> { it.second.isHard }
                    .thenByDescending { it.second.match.value.length }
                    .thenByDescending { it.second.weight }
            )

        for ((rule, found) in candidates) {
            if (claimed.any { it.overlaps(found.match.range) }) continue
            claimed += found.match.range
            hits += PatternHit(
                category = rule.category,
                matchedText = headline.substring(found.match.range),
                range = found.match.range,
                ruleId = found.ruleId,
                weight = found.weight,
                question = rule.questionFor(found, found.match.value),
            )
            if (found.isHard || found.isNotice) {
                hasHardOrNotice = true
            }
            // A category counts once no matter how many of its words appear.
            // Five fear words in one headline is still one technique, and
            // letting them stack would make the score meaningless.
            score += found.weight
        }

        // A combination consisting entirely of mild, soft signals (like "could" + "?"
        // or a single quote fragment + "could") without any loaded notice term or hard term
        // is ordinary responsible reporting, not manipulation. Cap it below the subtle threshold.
        if (!hasHardOrNotice && hits.isNotEmpty()) {
            score = minOf(score, pack.thresholds.subtle - SOFT_PAIR_MARGIN)
        }

        val level = when {
            score >= pack.thresholds.prominent -> FlagLevel.PROMINENT
            score >= pack.thresholds.subtle -> FlagLevel.SUBTLE
            else -> FlagLevel.NONE
        }
        return FlagResult(level, score, hits)
    }

    /**
     * Checks a headline and cross-references its feed summary to verify curiosity gaps
     * and extract the withheld explanatory context.
     */
    fun flagWithSummary(
        headline: String,
        summary: String?,
        sourceName: String? = null,
        articleType: ArticleType? = null,
    ): FlagResult =
        flag(headline, ArticleContext(articleType = articleType, summary = summary), sourceName)

    /**
     * [articleType] is what the article says it is, from its feed or link
     * (see ArticleTypeClassifier). An opinion piece, editorial, review or
     * letter is treated exactly like a headline that carries an opinion label
     * (codebook rule 2). [sourceName] only selects the user's own muted
     * phrases for that outlet; it never changes how a headline is judged.
     */
    fun flag(headline: String, sourceName: String? = null, articleType: ArticleType? = null): FlagResult =
        flag(headline, ArticleContext(articleType = articleType), sourceName)

    /** One match, what it is worth toward the score, and which rule found it. */
    private class Found(
        val match: MatchResult,
        val weight: Double,
        val isHard: Boolean = true,
        val isNotice: Boolean = false,
        val ruleId: String? = null,
    )


    private class CompiledCategory(
        val category: Category,
        val weight: Double,
        val noticeWeight: Double,
        val softWeight: Double,
        /** The most a run of soft words alone may score. See the note by its use below. */
        val softPairCap: Double,
        val terms: Regex?,
        val noticeTerms: Regex?,
        val softTerms: Regex?,
        val signals: List<String>,
        val noticeSignals: List<String>,
        val softSignals: List<String>,
        val cancels: Regex?,
        /** Signals that switch this whole category off. See [findIn]. */
        val cancelSignals: List<String>,
        /** Signals whose matched words this category must ignore. See [findIn]. */
        val muteSignals: List<String>,
        /** Rule-specific questions for detectors and compounds, by rule id. */
        val questions: Map<String, String>,
        /** Rule-specific questions for listed words; the regex matches any inflection. */
        val termQuestions: List<Pair<Regex, String>>,
        val compound: CompiledCompound?,
    ) {
        /** This rule's own question for the hit it just produced, or null to use the technique's. */
        fun questionFor(found: Found, matchedText: String): String? =
            if (found.ruleId?.startsWith("term:") == true) {
                termQuestions.firstOrNull { (regex, _) -> regex.matchEntire(matchedText) != null }?.second
            } else {
                found.ruleId?.let(questions::get)
            }

        fun findIn(headline: String, context: Signals.SignalContext, extraMutedRanges: List<IntRange> = emptyList()): Found? {
            // A headline that states its own limits has been straight with you,
            // and honesty must never be the thing that gets a headline marked.
            if (cancels?.containsMatchIn(headline) == true) return null
            if (cancelSignals.any { Signals.matches(it, headline, context) != null }) return null

            // Stretches of this headline that this category has to pretend
            // aren't there. Not the whole headline, just the words a muting
            // signal covers, plus any user-muted phrases. A named source attached
            // to a reporting verb mutes that verb, and nothing else: "Lina Khan warns"
            // stops "warns" counting as fear, while a frightening word elsewhere
            // in the same sentence still counts in full.
            val muted = muteSignals
                .flatMap { Signals.allMatches(it, headline, context) }
                .map { it.range } + extraMutedRanges

            fun audible(match: MatchResult) = muted.none { it.overlaps(match.range) }

            // Full-strength words and signals (>= 0.5) win if present.
            if (weight >= 0.5) {
                terms?.findAll(headline)?.firstOrNull(::audible)?.let { return Found(it, weight, isHard = true, ruleId = termId(it)) }

                for (signal in signals) {
                    Signals.allMatches(signal, headline, context)
                        .firstOrNull(::audible)
                        ?.let { return Found(it, weight, isHard = true, ruleId = "signal:$signal") }
                }

                compound?.findIn(headline, context)
                    ?.takeIf(::audible)
                    ?.let { return Found(it, weight, isHard = true, ruleId = "compound:${category.id}") }
            }

            // Loaded words that trigger a Pattern Notice on their own.
            noticeTerms?.findAll(headline)?.firstOrNull(::audible)
                ?.let { return Found(it, noticeWeight, isHard = false, isNotice = true, ruleId = termId(it)) }

            for (signal in noticeSignals) {
                Signals.allMatches(signal, headline, context)
                    .firstOrNull(::audible)
                    ?.let { return Found(it, noticeWeight, isHard = false, isNotice = true, ruleId = "signal:$signal") }
            }

            // Sub-notice terms (e.g. speculative_hedge where terms carry weight 0.35).
            if (weight < 0.5) {
                terms?.findAll(headline)?.firstOrNull(::audible)?.let { return Found(it, weight, isHard = false, ruleId = termId(it)) }

                for (signal in signals) {
                    Signals.allMatches(signal, headline, context)
                        .firstOrNull(::audible)
                        ?.let { return Found(it, weight, isHard = false, ruleId = "signal:$signal") }
                }

                compound?.findIn(headline, context)
                    ?.takeIf(::audible)
                    ?.let { return Found(it, weight, isHard = false, ruleId = "compound:${category.id}") }
            }

            for (signal in softSignals) {
                Signals.allMatches(signal, headline, context)
                    .firstOrNull(::audible)
                    ?.let { return Found(it, softWeight, isHard = false, ruleId = "signal:$signal") }
            }

            // One soft word is nothing: "warns" on its own is just reporting.
            // A second different one counts, further ones don't, so a
            // thesaurus-heavy headline can't run the score away.
            //
            // But a pair on its own is never enough to mark a headline, which
            // is what the cap here enforces. Hard news about frightening
            // things is full of these words used literally: "How climate
            // change worsened Nepal's deadly mountain collapse" is two fear
            // words describing an actual deadly collapse, and it was being
            // marked as manufactured fear for it. It takes a full-strength
            // word, or a second technique agreeing, to get over the line.
            //
            // The cost is known and was accepted, not overlooked. On the
            // reviewed corpus: five fewer wrongly flagged, one more missed.
            // On starter.tsv it costs the one hand-written case this rule was
            // originally built from: "Minister warns of dangerous
            // consequences if talks fail" now scores 0.45 and goes unmarked.
            // That headline is better caught as vague sourcing anyway, since
            // an unnamed "Minister" is exactly as vague as "officials say",
            // and vague_sourcing's list has "experts warn" but no equivalent
            // for a bare title. That is the fix; it needs its own measurement.
            val soft = softTerms?.findAll(headline)?.filter(::audible)?.toList().orEmpty()
            if (soft.isEmpty()) return null
            val distinct = soft.distinctBy { it.value.lowercase() }.size
            return Found(soft.first(), minOf(softWeight * minOf(distinct, 2), softPairCap), isHard = false, ruleId = termId(soft.first()))
        }
    }

    /**
     * Science overhype. Needs three things to be true at once:
     * a hedge, a big claim, and no mention of what stage the work is at.
     */
    private class CompiledCompound(
        val hedges: Regex?,
        val claims: Regex?,
        val claimSignals: List<String>,
        val cancels: Regex?,
        val domains: Regex?,
    ) {
        fun findIn(headline: String, context: Signals.SignalContext): MatchResult? {
            // If the headline already tells you it's lab-only or early-stage,
            // it has been straight with you. Never flag an honest headline.
            if (cancels?.containsMatchIn(headline) == true) return null

            if (hedges?.containsMatchIn(headline) != true) return null
            if (domains != null && domains.containsMatchIn(headline) != true) return null

            claims?.find(headline)?.let { return it }
            for (signal in claimSignals) {
                Signals.matches(signal, headline, context)?.let { return it }
            }
            return null
        }
    }
}

/**
 * Straightens curly quotes, turns dashes into "-", normalizes non-breaking and
 * zero-width spaces into standard spaces, and folds common Cyrillic confusable homoglyphs.
 *
 * Every replacement is strictly one character for one character, so match ranges
 * found in the result remain exact, valid ranges in the original headline.
 */
internal fun normalizeHeadline(headline: String): String {
    var hasReplacements = false
    for (i in headline.indices) {
        if (normalizeChar(headline[i]) != headline[i]) {
            hasReplacements = true
            break
        }
    }
    if (!hasReplacements) return headline

    val chars = headline.toCharArray()
    for (i in chars.indices) {
        chars[i] = normalizeChar(chars[i])
    }
    return String(chars)
}

private fun normalizeChar(c: Char): Char = when (c) {
    // Single quotes and apostrophes
    '’', '‘', '`', '‚', '′' -> '\''
    // Double quotes
    '“', '”', '„', '«', '»', '″' -> '"'
    // Dashes and hyphens
    '–', '—', '―', '‐', '‑' -> '-'
    // Zero-width and invisible format characters -> space
    '\u200B', '\u200C', '\u200D', '\u00AD', '\uFEFF' -> ' '
    // Non-breaking and atypical whitespace -> standard space
    '\u00A0', '\u202F', '\u2002', '\u2003', '\u2009', '\u3000' -> ' '
    // Common Cyrillic lowercase homoglyphs used to evade keyword filters
    '\u0430' -> 'a' // Cyrillic 'а'
    '\u0435' -> 'e' // Cyrillic 'е'
    '\u043E' -> 'o' // Cyrillic 'о'
    '\u0440' -> 'p' // Cyrillic 'р'
    '\u0441' -> 'c' // Cyrillic 'с'
    '\u0443' -> 'y' // Cyrillic 'у'
    '\u0445' -> 'x' // Cyrillic 'х'
    '\u0456' -> 'i' // Cyrillic 'і'
    '\u0458' -> 'j' // Cyrillic 'ј'
    // Common Cyrillic uppercase homoglyphs
    '\u0410' -> 'A' // Cyrillic 'А'
    '\u0412' -> 'B' // Cyrillic 'В'
    '\u0415' -> 'E' // Cyrillic 'Е'
    '\u041A' -> 'K' // Cyrillic 'К'
    '\u041C' -> 'M' // Cyrillic 'М'
    '\u041D' -> 'H' // Cyrillic 'Н'
    '\u041E' -> 'O' // Cyrillic 'О'
    '\u0420' -> 'P' // Cyrillic 'Р'
    '\u0421' -> 'C' // Cyrillic 'С'
    '\u0422' -> 'T' // Cyrillic 'Т'
    '\u0425' -> 'X' // Cyrillic 'Х'
    else -> c
}

/**
 * Turns a word list into one pattern that matches any of them as whole words,
 * ignoring capitals. Whole-word matching matters: without it, "may" would match
 * inside "mayor" and "dismay".
 */
internal fun Collection<String>.toAlternationRegex(): Regex? {
    if (isEmpty()) return null
    val body = sortedByDescending { it.length } // prefer the longest match
        .joinToString("|") { term ->
            val escaped = if (term.contains(' ')) {
                term.split(" ").joinToString("""['"]?\s+['"]?""") { Regex.escape(it) }
            } else {
                inflectedPattern(term)
            }
            val startBoundary = if (term.first().isLetterOrDigit()) """\b""" else ""
            val endBoundary = if (term.last().isLetterOrDigit()) """\b""" else ""
            """$startBoundary$escaped$endBoundary"""
        }
    return Regex(body, RegexOption.IGNORE_CASE)
}

/**
 * Allows the ordinary inflections of a single word, handling silent 'e' drops
 * (plunge -> plunging/plunged) and consonant 'y' alternations (fury -> furies),
 * so word lists do not need separate entries for each morphological variant.
 *
 * Only applied to single words. Phrases are matched with exact whitespace.
 */
internal fun inflectedPattern(term: String): String {
    if (term.contains(' ') || term.length < 4 || !term.all { it.isLetter() }) {
        return Regex.escape(term)
    }
    val lower = term.lowercase()
    return when {
        // Words ending in 'ee' (e.g. agree): agrees, agreed, agreeing
        lower.endsWith("ee") -> {
            Regex.escape(term) + "(?:s|d|ing)?"
        }
        // Words ending in silent 'e' (e.g. plunge, strike, savage, collapse):
        // plunge -> plunge, plunges, plunged, plunging
        lower.endsWith("e") -> {
            val stem = Regex.escape(term.dropLast(1))
            "$stem(?:e|es|ed|ing)"
        }
        // Words ending in consonant + 'y' (e.g. fury, worry):
        // fury -> fury, furies, furied, furying
        lower.endsWith("y") && lower.length >= 2 && lower[lower.length - 2] !in "aeiou" -> {
            val stem = Regex.escape(term.dropLast(1))
            "$stem(?:y|ies|ied|ying)"
        }
        // Standard regular inflection:
        // warn -> warns, warned, warning
        else -> {
            Regex.escape(term) + "(?:s|es|ed|ing)?"
        }
    }
}

/** True if two stretches of text share any characters. */
private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

/**
 * How far below the quiet threshold a run of soft words alone is held.
 *
 * Derived from the threshold rather than written down as 0.45, so that moving
 * the threshold in the rule file cannot silently turn "a pair is never enough
 * on its own" into "a pair is plenty".
 */
private const val SOFT_PAIR_MARGIN = 0.05

/** How much of a story the context check reads: the opening, as ArticleText.extract keeps by default. */
private const val STORY_CHARS = 4000

/** The rule id a word-list match reports in its hit: "term:slams". */
private fun termId(match: MatchResult) = "term:" + match.value.lowercase()
