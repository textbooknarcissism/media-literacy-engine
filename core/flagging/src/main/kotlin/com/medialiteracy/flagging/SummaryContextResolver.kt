package com.medialiteracy.flagging

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import com.medialiteracy.model.FlagResult
import com.medialiteracy.model.PatternHit

/**
 * Uses an article's feed summary / blurb to verify curiosity gaps and extract
 * the explanatory context that the headline withheld.
 *
 * Zero AI, zero network calls, pure Kotlin. Deliberately conservative: it
 * attaches context to techniques the headline already earned, and adds a new
 * flag only for the two headline-body conflicts: a buried lab-stage caveat
 * and an official walk-back of the headline's alarm.
 */
object SummaryContextResolver {

    private val JUNK_BLOCKS = Regex(
        """<(script|style|noscript|svg|head|nav|header|footer|aside|form|figure)\b[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val COMMENTS = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<[^>]+>""")

    /** Common RSS trailing noise or syndication boilerplate. */
    private val RSS_BOILERPLATE = Regex(
        """(?i)\b(?:the\s+post\b.*?(?:first\s+appeared\s+on|appeared\s+first\s+on)\b.*|read\s+more\b.*|continue\s+reading\b.*|read\s+full\s+article\b.*|photo\s+(?:by|credit)\b.*|copyright\s+\d{4}\b.*|all\s+rights\s+reserved\b.*|follow\s+us\s+on\b.*)""",
    )

    private val SENTENCE_BOUNDARY = Regex(
        """(?<=[.!?])(?<!\b(?:Dr|Mr|Mrs|Ms|Prof|Sen|Rep|Gov|Gen|Col|Capt|Lt|St|vs|U\.S)\.)\s+(?=[A-Z0-9"'])""",
    )

    /** Caveats appearing directly in the headline proving it was honest and didn't bury the stage. */
    private val HEADLINE_SCIENCE_CAVEAT = Regex(
        """\b(?:in\s+(?:laboratory\s+)?(?:mice|rats|rodents|animals?|nematodes?|worms?|fruit\s+flies|zebrafish|pigs?|monkeys?)|in\s+(?:the\s+)?lab(?:\s+tests?)?|lab-scale|early-stage|in\s+simulations?|small-scale|in\s+vitro|in\s+(?:a\s+)?petri\s+dish|preclinical|pre-clinical|in\s+cell\s+cultures?|phase\s+[1i]\b|in\s+a\s+dish)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Preclinical, animal model, or lab-stage research indicators in summaries. */
    private val SUMMARY_LAB_CAVEAT = Regex(
        """\b(?:in\s+(?:laboratory\s+)?(?:mice|rats|rodents|animals?|nematodes?|worms?|fruit\s+flies|zebrafish|pigs?|monkeys?)|murine\s+model|mouse\s+model|rat\s+model|animal\s+model|(?:human\s+)?(?:cancer\s+)?cell\s+lines?|microfluidic\s+(?:chip|device)|organoids?|in\s+(?:the\s+)?lab(?:\s+tests?)?|in\s+cell\s+cultures?|in\s+(?:a\s+)?petri\s+dish|in\s+vitro|in\s+a\s+dish|in\s+test\s+tubes?|lab-grown|preclinical|pre-clinical|early-stage\s+research|phase\s+(?:1|i)\s+trial|in\s+simulations?|computer\s+models?|mathematical\s+model)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Major medical/scientific breakthrough claims in headlines that warrant checking for buried caveats. */
    private val HEADLINE_SCIENCE_CLAIM = Regex(
        """\b(?:(?:could\s+|may\s+|might\s+|can\s+)?(?:cures?|curing|halts?|halting|reverses?|reversing|eliminates?|eliminating|wipes?\s+out|wiping\s+out|stops?|stopping|prevents?|preventing|kills?|killing|destroys?|destroying|combats?|combating|eradicates?|eradicating|treats?|treating)\s+(?:nearly\s+all\s+|all\s+)?(?:cancer|tumou?rs?|leukemia|alzheimer'?s(?:\s+(?:memory\s+loss|disease|symptoms?))?|dementia|parkinson'?s(?:\s+disease)?|diabetes|age?ing|baldness|obesity|memory\s+loss|hearing\s+loss|blindness|paralysis|infections?|viruses?|disease)|miracle\s+cure|breakthrough\s+(?:drug|treatment|therapy|pill)|revolutionary\s+(?:drug|treatment|therapy|pill)|game-changing\s+therapy|cure\s+for\s+(?:cancer|alzheimer'?s|dementia|age?ing|baldness|diabetes|parkinson'?s)|(?:compound|drug|molecule|therapy|treatment|antibody|pill|method|way)\s+(?:that\s+)?(?:could\s+|may\s+|might\s+|can\s+)?(?:cures?|halts?|reverses?|eliminates?|wipes?\s+out|stops?|prevents?|kills?|destroys?)\s+(?:cancer|tumou?rs?|leukemia|alzheimer'?s(?:\s+(?:memory\s+loss|disease|symptoms?))?|dementia|parkinson'?s(?:\s+disease)?|diabetes|age?ing|baldness|obesity|memory\s+loss|disease))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Officials or experts de-escalating or walking back alarm in summary text. */
    private val SUMMARY_WALK_BACK = Regex(
        """\b(?:officials?|authorities|police|experts?|spokesperson|agency)\s+(?:stressed|said|clarified|confirmed|noted)\s+(?:there\s+is\s+)?(?:no\s+(?:immediate\s+)?(?:threat|danger|risk|damage|casualties|injuries)|risk\s+(?:is|remains)\s+(?:low|minimal|negligible))\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * An evaluative claim credited to a named person in the summary.
     *
     * Only the verbs and role words ignore case. The name must be capitalized:
     * with the whole pattern case-insensitive, `[A-Z][a-z]+` matched any two
     * words, so "argues that president" counted as a name.
     */
    private val SUMMARY_ATTRIBUTION_SHIFT = Regex(
        """\b(?i:writes?|wrote|argues?|argued|claims?|claimed|according\s+to)\s+""" +
            """(?:(?i:shadow|former|deputy)\s+)?""" +
            """(?:(?i:columnist|commentator|critic|opposition|campaigner|minister|chancellor|secretary|leader|senator|mp|spokesperson)\s+)?""" +
            """[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+\b""",
    )

    /** Headline forms that already say whose words these are: "X: 'quote'", "'quote': X", "..., says X". */
    private val HEADLINE_ATTRIBUTED_FORM = Regex(
        """^[^:]{2,80}:\s*['"‘“]|['"’”]\s*:\s*\S|,\s+(?i:says?|said|claims?|warns?|argues?)\s+[A-Z]""",
    )

    private fun isAttributedInHeadline(headline: String): Boolean =
        HEADLINE_ATTRIBUTED_FORM.containsMatchIn(headline) ||
            Signals.allMatches("named_attribution", headline, emptySet()).isNotEmpty()

    /** Fear / alarmist cues in headline for walk-back matching. */
    private val HEADLINE_FEAR_CUE = Regex(
        """\b(?:crisis|blackouts?|surges?|surging|panic|devastation|danger|catastrophic|warning|threat|threatens?|disaster|collapse)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Explicit negative/debunking resolution answering a Betteridge or speculative question in summary text. */
    private val SUMMARY_BETTERIDGE_ANSWER = Regex(
        """\b(?:the\s+answer\b.*?\b(?:is|remains)\s+(?:an?\s+)?(?:emphatic\s+|resounding\s+|simple\s+|plain\s+|clear\s+|unequivocal\s+)?(?:no|yes|neither|unclear|complicated)|""" +
            """short\s+answer\s+is\s+(?:no|yes)|""" +
            """far\s+from\s+(?:being\s+)?(?:the\s+worst|the\s+case|true|reality|unmitigated)|""" +
            """(?:critics?|experts?|analysts?|researchers?|scientists?|historians?|officials?)\s+(?:say|argue|conclude|found|emphasize|note|confirmed)\s+(?:the\s+answer\s+is\s+no|it\s+is\s+not|that\s+is\s+unlikely|there\s+is\s+no\s+evidence)|""" +
            """there\s+is\s+no\s+(?:evidence|proof|indication|reason\s+to\s+believe|chance)\s+that|""" +
            """remains\s+(?:unlikely|far\s+off|decades\s+away|a\s+distant\s+prospect))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Stance or thesis answering a trailing debate nudge or rhetorical debate prompt. */
    private val SUMMARY_DEBATE_STANCE = Regex(
        """\b(?:(?:argues?|argued|contends?|contended|maintains?|maintained|insists?|insisted|concludes?|concluded|warns?|warned|points?\s+out|reasons?)\s+(?:that\s+)?|""" +
            """(?:writes?|wrote|says?|said)\s+that|""" +
            """(?:the\s+(?:short\s+)?answer\s+is|the\s+verdict\s+is|the\s+reality\s+is|in\s+reality|far\s+from\s+it|not\s+so\s+fast)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Yes/no question headline opening with an auxiliary or modal verb. */
    private val HEADLINE_RHETORICAL_OR_POLAR_QUESTION = Regex(
        """^\W*(?:Has|Have|Did|Is|Are|Was|Were|Could|Can|Will|Would|Should|Do|Does)\s+[^|]+\?\s*(?:\|.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** Phrases in summaries that indicate the summary ALSO withholds the answer (double tease). */
    private val SUMMARY_TEASE = Regex(
        """(?i)\b(?:find\s+out\s+why|click\s+(?:here|to\s+read)|read\s+on\s+to\s+find|you\s+won't\s+believe|see\s+what\s+happens|here's\s+what\s+you\s+need\s+to\s+know)\b""",
    )

    private val STOPWORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with",
        "about", "against", "between", "into", "through", "during", "before", "after",
        "above", "below", "from", "up", "down", "over", "under", "again", "further",
        "then", "once", "here", "there", "when", "where", "why", "how", "all", "any",
        "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor",
        "not", "only", "own", "same", "so", "than", "too", "very", "can", "will",
        "just", "should", "now", "says", "said", "reveals", "revealed", "reports", "reported"
    )

    /** Strips HTML, entities and syndication boilerplate from a feed summary. Public so tools store summaries exactly as the resolver reads them. */
    fun sanitize(rawSummary: String): String {
        return rawSummary
            .replace(COMMENTS, " ")
            .replace(JUNK_BLOCKS, " ")
            .replace(TAG, " ")
            .let(::decodeEntities)
            .replace(RSS_BOILERPLATE, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")

    /**
     * Examines [summary] to find the explanatory sentence answering the headline's
     * curiosity gap, resolving fear walk-backs, or disclosing attribution shifts.
     */
    fun resolve(
        headline: String,
        summary: String?,
        existingResult: FlagResult,
    ): FlagResult {
        if (summary.isNullOrBlank()) return existingResult

        val cleanSummary = sanitize(summary)
        if (cleanSummary.length < 35) return existingResult

        val headlineAnchorWords = extractAnchorWords(headline)

        val sentences = cleanSummary.split(SENTENCE_BOUNDARY)
            .map { it.trim() }
            .filter { it.length >= 35 }

        var current = existingResult

        // 1. Science overhype caveat resolution: checks for buried animal/lab-stage testing
        current = resolveScienceOverhype(headline, sentences, headlineAnchorWords, current)

        // 2. Fear walk-back resolution: checks for official de-escalation of alarming headline
        current = resolveWalkBack(headline, sentences, headlineAnchorWords, current)

        // 3. Attribution shift resolution: checks for partisan/columnist sourcing of un-attributed opinion
        current = resolveAttributionShift(headline, sentences, headlineAnchorWords, current)

        // 4. Rhetorical debate & question stance resolution: extracts thesis or verdict for debate nudges / Betteridge traps
        current = resolveRhetoricalDebate(headline, sentences, headlineAnchorWords, current)

        // 5. Curiosity gap resolution: checks for missing fact answering the headline's tease
        current = resolveCuriosityGap(headline, sentences, headlineAnchorWords, current)

        return current
    }

    private fun resolveScienceOverhype(
        headline: String,
        sentences: List<String>,
        anchorWords: Set<String>,
        current: FlagResult,
    ): FlagResult {
        if (HEADLINE_SCIENCE_CAVEAT.containsMatchIn(headline)) return current

        val isOverhypeFlagged = current.categories.contains(Category.OVERHYPE)
        val claimMatch = HEADLINE_SCIENCE_CLAIM.find(headline)

        if (!isOverhypeFlagged && claimMatch == null) return current

        val candidate = sentences.firstOrNull { sentence ->
            !isDuplicateOfHeadline(sentence, headline) &&
                sharesTopicAnchor(sentence, anchorWords) &&
                SUMMARY_LAB_CAVEAT.containsMatchIn(sentence)
        } ?: return current

        return if (isOverhypeFlagged) {
            current.copy(resolvedContext = current.resolvedContext ?: candidate)
        } else {
            val claim = claimMatch ?: return current
            val hit = PatternHit(
                category = Category.OVERHYPE,
                matchedText = claim.value,
                range = claim.range,
                ruleId = "summary:lab_caveat",
                weight = 0.5,
            )
            val updatedHits = current.hits + hit
            val updatedScore = maxOf(current.score, 0.50)
            val updatedLevel = if (updatedScore >= 1.0) FlagLevel.PROMINENT else FlagLevel.SUBTLE
            FlagResult(
                level = updatedLevel,
                score = updatedScore,
                hits = updatedHits,
                resolvedContext = current.resolvedContext ?: candidate,
            )
        }
    }

    private fun resolveWalkBack(
        headline: String,
        sentences: List<String>,
        anchorWords: Set<String>,
        current: FlagResult,
    ): FlagResult {
        val isFearFlagged = current.categories.contains(Category.FEAR)
        val hasFearCue = isFearFlagged || HEADLINE_FEAR_CUE.containsMatchIn(headline)
        if (!hasFearCue) return current

        val candidate = sentences.firstOrNull { sentence ->
            !isDuplicateOfHeadline(sentence, headline) &&
                sharesTopicAnchor(sentence, anchorWords) &&
                SUMMARY_WALK_BACK.containsMatchIn(sentence)
        } ?: return current

        return if (isFearFlagged) {
            current.copy(resolvedContext = current.resolvedContext ?: candidate)
        } else {
            val cue = HEADLINE_FEAR_CUE.find(headline) ?: return current
            val fearHit = PatternHit(
                category = Category.FEAR,
                matchedText = cue.value,
                range = cue.range,
                ruleId = "summary:walk_back",
                weight = 0.5,
            )
            val updatedHits = current.hits + fearHit
            val updatedScore = maxOf(current.score, 0.50)
            val updatedLevel = if (updatedScore >= 1.0) FlagLevel.PROMINENT else FlagLevel.SUBTLE
            FlagResult(
                level = updatedLevel,
                score = updatedScore,
                hits = updatedHits,
                resolvedContext = current.resolvedContext ?: candidate,
            )
        }
    }

    private fun resolveAttributionShift(
        headline: String,
        sentences: List<String>,
        anchorWords: Set<String>,
        current: FlagResult,
    ): FlagResult {
        // Context only: this attaches the summary's attribution to an opinion
        // flag the headline already earned. It never creates a flag, because a
        // loose cue list ("should", "is a ...") plus any named person in the
        // summary flagged straight news reports.
        if (!current.categories.contains(Category.OPINION)) return current
        if (headline.contains('|')) {
            val bylineAfterPipe = headline.substringAfterLast('|').trim()
            if (bylineAfterPipe.split(' ').size in 1..4) return current
        }
        if (isAttributedInHeadline(headline)) return current

        val candidate = sentences.firstOrNull { sentence ->
            !isDuplicateOfHeadline(sentence, headline) &&
                sharesTopicAnchor(sentence, anchorWords) &&
                SUMMARY_ATTRIBUTION_SHIFT.containsMatchIn(sentence)
        } ?: return current

        return current.copy(resolvedContext = current.resolvedContext ?: candidate)
    }

    private fun resolveRhetoricalDebate(
        headline: String,
        sentences: List<String>,
        anchorWords: Set<String>,
        current: FlagResult,
    ): FlagResult {
        // Context only: attaches the author thesis, editorial stance, or debunking verdict
        // to a debate nudge or rhetorical question the headline already earned.
        // Never creates a flag on its own.
        if (!current.isFlagged) return current

        val hasTrailingDebate = Signals.allMatches("trailing_rhetorical_question", headline, emptySet()).isNotEmpty()
        val hasBetteridge = Signals.allMatches("betteridge_superlative_question", headline, emptySet()).isNotEmpty()
        val hasRhetoricalQuestion = hasTrailingDebate || hasBetteridge ||
            (headline.contains('?') && (
                current.categories.contains(Category.OPINION) ||
                current.categories.contains(Category.CURIOSITY) ||
                current.categories.contains(Category.HEDGE)
            ))

        if (!hasRhetoricalQuestion) return current

        fun matchesAnswer(sentence: String): Boolean =
            (sharesTopicAnchor(sentence, anchorWords) || SUMMARY_BETTERIDGE_ANSWER.containsMatchIn(sentence)) &&
                !isDuplicateOfHeadline(sentence, headline) &&
                !SUMMARY_TEASE.containsMatchIn(sentence)

        val candidate = if (hasBetteridge || HEADLINE_RHETORICAL_OR_POLAR_QUESTION.containsMatchIn(headline)) {
            sentences.firstOrNull { sentence ->
                matchesAnswer(sentence) &&
                    (SUMMARY_BETTERIDGE_ANSWER.containsMatchIn(sentence) || SUMMARY_DEBATE_STANCE.containsMatchIn(sentence))
            }
        } else {
            sentences.firstOrNull { sentence ->
                matchesAnswer(sentence) &&
                    (SUMMARY_DEBATE_STANCE.containsMatchIn(sentence) || SUMMARY_BETTERIDGE_ANSWER.containsMatchIn(sentence))
            }
        } ?: return current

        return current.copy(resolvedContext = current.resolvedContext ?: candidate)
    }

    private fun resolveCuriosityGap(
        headline: String,
        sentences: List<String>,
        anchorWords: Set<String>,
        current: FlagResult,
    ): FlagResult {
        // Context only: the summary can supply the answer to a tease the
        // headline was already flagged for. Sharing one word with the summary
        // is not evidence that the headline withheld anything.
        if (!current.categories.contains(Category.CURIOSITY)) return current

        val candidate = sentences.firstOrNull { sentence ->
            !isDuplicateOfHeadline(sentence, headline) &&
                sharesTopicAnchor(sentence, anchorWords) &&
                !SUMMARY_TEASE.containsMatchIn(sentence)
        } ?: return current

        return current.copy(resolvedContext = current.resolvedContext ?: candidate)
    }

    private fun extractAnchorWords(headline: String): Set<String> {
        val words = headline
            .split(Regex("""[^A-Za-z0-9]+"""))
            .map { it.lowercase() }
            .filter { it.isNotEmpty() && it !in STOPWORDS }
            .toMutableSet()
        if ("doctor" in words) words.add("dr")
        if ("dr" in words) words.add("doctor")
        if ("scientists" in words || "scientist" in words) {
            words.addAll(listOf("researchers", "researcher", "team", "study", "authors"))
        }
        if ("researchers" in words || "researcher" in words) {
            words.addAll(listOf("scientists", "scientist", "team"))
        }
        if ("drug" in words || "pill" in words) {
            words.addAll(listOf("compound", "therapy", "treatment", "molecule"))
        }
        if ("cure" in words || "reverse" in words || "halt" in words) {
            words.addAll(listOf("restore", "restored", "extend", "extended", "reduce", "reduced"))
        }
        if ("aging" in words || "ageing" in words) {
            words.addAll(listOf("lifespan", "longevity"))
        }
        if ("movie" in words || "film" in words) {
            words.addAll(listOf("film", "movie", "cinema", "actor", "director", "dialogue", "scenes"))
        }
        if ("alzheimer" in words || "alzheimers" in words || "memory" in words || "dementia" in words) {
            words.add("cognitive")
        }
        if ("tax" in words || "taxes" in words) {
            words.addAll(listOf("cuts", "spending", "finances", "budget", "economic"))
        }
        if ("reform" in words || "reforms" in words) {
            words.addAll(listOf("proposal", "proposed", "changes", "cuts", "policy", "finances"))
        }
        if ("blackout" in words || "blackouts" in words || "power" in words) {
            words.addAll(listOf("grid", "electricity", "energy", "cuts"))
        }
        return words
    }

    private fun isDuplicateOfHeadline(sentence: String, headline: String): Boolean {
        val s = sentence.trim().lowercase().trimEnd('.', '!', '?')
        val h = headline.trim().lowercase().trimEnd('.', '!', '?')
        return s == h || s.startsWith(h) || h.startsWith(s)
    }

    private fun sharesTopicAnchor(sentence: String, anchorWords: Set<String>): Boolean {
        if (anchorWords.isEmpty()) return true
        val sentenceWords = sentence
            .split(Regex("""[^A-Za-z0-9]+"""))
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return anchorWords.any { anchor ->
            sentenceWords.any { word ->
                word == anchor ||
                    (word.length > 3 && anchor.length > 3 && (word.startsWith(anchor) || anchor.startsWith(word)))
            }
        }
    }
}
