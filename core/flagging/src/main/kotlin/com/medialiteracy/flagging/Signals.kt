package com.medialiteracy.flagging

import com.medialiteracy.model.ArticleType

/**
 * Detectors for things that are patterns rather than words: you cannot put
 * "shouting" or "scare quotes" in a word list.
 *
 * Every rule in here earned its place by being visible in real harvested
 * headlines. The one that didn't (ALL-CAPS as urgency) was deleted after real
 * feeds showed tabloids lead with the writer's name in caps ("SARAH VINE:") and
 * broadcasters label format ("WATCH LIVE:").
 */
internal object Signals {

    /** Any run of spaces, for splitting a headline into words. */
    private val WHITESPACE = Regex("""\s+""")

    /** Two or more exclamation marks. A single one is common and harmless. */
    private val MANY_BANGS = Regex("""!(?:[^!]*!)+""")

    /**
     * A concrete performance claim: a number attached to a unit or a magnitude.
     * Deliberately narrow: a bare number like a date or an age is not a claim.
     *
     * Two things this is not a claim about, found by real false positives:
     * a **price** ("£3.2million Scottish castle") is a fact about what
     * something costs, not a performance claim, so a currency symbol right
     * before the number rules it out. And **rarity** ("one in 163 million
     * daughter") is a statement about how uncommon something is, not a claim
     * about what it does, so "one in " right before the number rules that out
     * too.
     *
     * Both exclusions have to bind to the true *start* of the number, not
     * wherever the regex engine next finds a digit. The first version of this
     * fix used `\b\d` for that start, but `\b` is a word boundary, and a dot or
     * comma is not a word character, so for "£3.2million" it correctly
     * refused to start at the "3" right after "£", then just started over at
     * the "2" right after the dot instead, and matched "2million" as if the
     * price were "2 million". `(?<![\d,.])` closes that: it refuses to start a
     * match anywhere still inside a longer run of digits, dots or commas, so
     * the only place left to start is the true first digit: exactly where the
     * currency and "one in " checks are looking.
     */
    private val NUMERIC_CLAIM = Regex(
        """(?<![£$€])(?<![\d,.])(?<!one in )\d[\d,.]*\s*(%|x\b|percent|times|million|billion|trillion|""" +
            """miles|km|kilometres|kilometers|hours|minutes|days|""" +
            """degrees|gigawatts|terabytes|mph)\b""" +
            // "240million years ago" is a fact about the past, not a claim about
            // what something will do.
            """(?!\s*years?\s+ago)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Headlines that open by promising a reveal instead of giving you the news:
     * "Unmasked: the reality star behind...", "Revealed how...".
     *
     * Several openings are deliberately absent, all for the same reason: they
     * say what a piece *is* rather than dangling what is inside it.
     *
     * - "Watch:". Broadcasters use it to label a video.
     * - "Exclusive:". A scoop is a claim about who got the story first, not a
     *   tease about what it says. Nature, Ars Technica and Breitbart all used
     *   it straight in the corpus and were flagged at full strength for it.
     * - "Pictured:". A format note, exactly like "Watch:". It was marking
     *   "Pictured: Member of British armed forces, 40, killed in Ukraine
     *   crash" as a click device, which is about as wrong as this gets.
     * - "Revealed:" with a colon. It is a label, like the three above. "Revealed"
     *   without one ("Revealed how...") still counts.
     * - "Moment". Removed after the 2026-10-10 relabel. "Moment thug stabs
     *   man twice before police dog drags him to the ground" says what
     *   happens; the word marks a video, and nothing is held back, so the
     *   codebook's definition of withheld detail does not apply.
     */
    private val TEASER_PREFIX = Regex(
        """^\W*(Unmasking|Unmasked|Revealed(?!\s*:))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A headline posed as a question rarely answers it above the fold. */
    private val QUESTION = Regex("""\?\s*$""")

    /**
     * A quoted span. The lookarounds stop a possessive apostrophe from being
     * read as an opening quote: without them, "Huw Edwards' plans for a
     * 'comeback'" matched from the apostrophe in the name to the next quote and
     * reported nonsense.
     */
    private val QUOTED = Regex("""(?<![\p{L}\p{N}])['‘"]([^'’"]{2,60})['’"](?![\p{L}\p{N}])""")

    /**
     * Everything inside a pair of quotation marks, however long. Codebook
     * rule 1: those are the quoted person's words, not the outlet's.
     *
     * Used only to mute the tone categories, never to mark anything. "Amanda
     * Redman: 'My most embarrassing moment? ...'" was charged with opinion as
     * fact for a word the actress used about herself. Before the 2026-10-10
     * fixture repair it stayed quiet only by accident: a corrupted closing quote
     * made the span a scare quote, whose longer match won the overlap.
     *
     * [HeadlineFlagger] straightens quotes before any signal runs, so only ' and
     * " arrive here. An apostrophe between two letters ("I'm", "Kelce's") is
     * part of the quotation, not its end.
     *
     * Scare quotes are unaffected: vague sourcing has its own [scareQuotes]
     * signal and does not mute this one.
     */
    private val QUOTED_SPEECH = Regex(
        """(?<![\p{L}\p{N}])'(?:[^'"]|(?<=\p{L})'(?=\p{L}))+'(?![\p{L}\p{N}])""" + "|\"[^\"]+\"",
    )

    /**
     * "BREAKING:" as a tabloid urgency label: never "breaking" as an ordinary
     * verb. Without the colon, this matched "Breaking down the WNBA playoff
     * picture", which has no urgency in it at all; "breaking" there just means
     * "analysing".
     */
    private val BREAKING_LABEL = Regex("""^\W*Breaking(?:\s+News)?\s*:""", RegexOption.IGNORE_CASE)

    /**
     * "Just in:" as an urgency label, the same fix as [BREAKING_LABEL] and for
     * the same reason. As a bare phrase in the word list it matched "farmers
     * are 'all just in survival mode'", where "just in" is not a label at all:
     * it is two ordinary words that happen to sit next to each other.
     */
    private val JUST_IN_LABEL = Regex("""^\W*Just\s+in\s*:""", RegexOption.IGNORE_CASE)

    /**
     * A named, identifiable person or organization immediately followed by a
     * neutral reporting verb: "King Charles warns", "Microsoft says", "FTC
     * chair Lina Khan warns". Deliberately excludes the outlet's own loaded
     * verbs ("slams", "blasts"): this is only for the plainest, most neutral
     * verbs of attribution.
     *
     * See [namedAttribution] for the second half of this: the subject also has
     * to not be one of vague sourcing's own vague-role words, or this would
     * cancel the category on its own defining phrase: "Sources say" starts
     * with a capital letter too.
     *
     * **This mutes the words it covers; it does not switch a category off.**
     * That distinction is the whole point and it was learned the expensive way.
     * The first version switched off the entire category for the headline, and
     * on manufactured_fear that wiped out the outlet's own unquoted words
     * whenever a name appeared anywhere else in the same sentence: "DR
     * PHILIPPA KAYE reveals ticking timebomb of side effects" lost its flag,
     * because "ticking timebomb" is fear's own hard term and has nothing to do
     * with the byline. So it was scoped to vague_sourcing only, which hid the
     * problem rather than fixing it.
     *
     * Muting a span fixes it properly. "Lina Khan warns" covers the word
     * "warns", so fear cannot count it: a reporting verb attached to a named
     * source is grammar, not the outlet choosing a frightening word. But
     * "ticking timebomb" sits outside that span and still counts at full
     * strength. Both headlines now come out right, and the rule can be applied
     * to fear and vague sourcing alike.
     */
    private val NAMED_ATTRIBUTION = Regex(
        """\b((?:[A-Z][\p{L}'.-]*)(?:\s+(?!(?i:as|after|when|while|amid|over|before|since|until|despite|if|because|following|at|against|about|into|makes|make|made|fumes|fumed|hits|slams|blasts)\b)(?:[A-Z][\p{L}'.-]*|de|van|von|der|la|le|da|di|du|of|the|and|&)){0,3}(?:\s+(?:experts?|officials?|researchers?|scientists?|analysts?|spokes(?:person|man|woman)|leaders?|aides?|team|chiefs?))?)\s+""" +
            """(?:says|say|said|tells|tell|told|warns|warn|warned|""" +
            """claims|claim|claimed|insists|insist|insisted|""" +
            """reveals|reveal|revealed|admits|admit|admitted|""" +
            """threatens|threaten|threatened|asks|ask|asked|""" +
            """argues|argue|argued|announces|announce|announced|adds|add|added)\b"""
    )

    /**
     * The vague, unnamed roles vague_sourcing's own word list already targets:
     * "sources say", "experts warn". Capitalized at the start of a headline
     * they look exactly like a name to [NAMED_ATTRIBUTION], so a match is only
     * real attribution if at least one word in the subject isn't one of these.
     */
    private val VAGUE_ROLE_WORDS = setOf(
        "sources", "source", "experts", "expert", "insiders", "insider",
        "officials", "official", "critics", "critic", "analysts", "analyst",
        "researchers", "researcher", "scientists", "scientist", "reports",
        "report", "rumours", "rumors", "rumour", "rumor", "people", "some",
        "many", "police", "authorities", "authority", "pals", "friends",
        "they", "observers", "aides", "allies", "ally", "officer", "commissioner",
        "prime", "senior", "top", "former", "chief", "lead", "head",
        // An unnamed titleholder ("Minister warns...") is exactly as vague as
        // "officials say": the title alone names a role, not a checkable
        // person. Caught by a hand-written test after this fix shipped: a
        // named minister ("Health Secretary Wes Streeting warns...") still
        // cancels correctly, because the name sitting next to the title is
        // what makes the subject not-all-vague: see the "any" check below.
        "minister", "president", "chancellor", "senator", "governor", "mayor",
        "secretary", "spokesperson", "spokesman", "spokeswoman", "chairman",
        "chairwoman",
        // A whole population is not a source. "Americans warned MORE hikes
        // could be coming" names nobody (it is the outlet's own warning with
        // a nationality in front of it) but a demonym is capitalized, so it
        // looked exactly like a name. This is the headline that made the first
        // attempt at attribution get reverted, so it is a real one, not a
        // hypothetical.
        //
        // A word list can never finish this job; these are the groups that
        // actually turned up in the corpus. Anything missed fails safe in the
        // wrong direction (a headline goes unmarked), so add to it whenever
        // the eval shows a new one.
        "americans", "britons", "brits", "europeans", "russians", "ukrainians",
        "australians", "canadians", "germans", "french", "chinese", "indians",
        "israelis", "palestinians", "parents", "drivers", "motorists",
        "shoppers", "households", "families", "workers", "pensioners",
        "students", "patients", "voters", "customers", "passengers",
        "travellers", "travelers", "tourists", "residents", "homeowners",
        "savers", "commuters", "locals", "women", "men", "children",
    )

    /**
     * Reports of concrete physical disasters, casualties, accidents, armed clashes, labor strikes,
     * facility shutdowns, social celebrations, or official security alerts:
     * "deadly mountain collapse", "deadly shooting", "21 killed as building collapses",
     * "threat warning level raised to 'critical'", "26 killed in clashes along border",
     * "hurricane destroys hundreds of homes", "workers strike at Amazon over pay",
     * "plant shuts down", "birthday bash".
     *
     * In these cases, words like "deadly", "collapse", "threat", "warning", "fatal", "clashes",
     * "destroys", "strike at", "shuts down", "bash" describe literal events, labor disputes,
     * physical facilities, or social parties in the real world, not psychological manipulation.
     * Muting these spans stops hard news coverage of real tragedies, industrial reporting, and
     * social events from being marked as manufactured fear or manufactured rage.
     *
     * The same goes for a word used in another sense: weather or a vehicle that slams
     * ("Storm Amy slams Scotland", "lorry slams into bridge"), "blasted" as an adjective
     * ("the blasted beast"), and "alarming" as a verb with an object ("expand strikes,
     * alarming the U.S."), which reports somebody's reaction.
     */
    private val LITERAL_EVENT_OR_ALERT = Regex(
        """\b(?:""" +
            """(?:deadly|fatal)\s+(?:\w+\s+){0,2}(?:blast|crash|collision|shooting|strike|fire|storm|accident|tornado|hurricane|earthquake|collapse|derailment|attack|drowning|landslide|avalanche)\b|""" +
            """(?<!\bhorror\s)(?:mountain|glacier|rock|wall|building|bridge|dam|crane|roof|structure)\s+collapses?\b|""" +
            """\bwar[- ]damaged\s+\w+\s+collapses?\b|""" +
            """(?:terror\s+threat|threat\s+level|warning\s+level|alert\s+level|pandemic\s+alert)\b|""" +
            """\b(?:\d+\s+)?(?:killed|dead|injured|casualties)\s+in\s+(?:armed\s+|military\s+|border\s+)?clashes?\b|""" +
            """\bclashes?\s+along\s+(?:[\w-]+\s+){0,3}border\b|""" +
            """\b(?:armed|military|border)\s+clashes?\b|""" +
            """(?:death\s+toll|casualties|\d+\s+(?:dead|killed|injured))\b|""" +
            """\b(?:bomb|blast|fire|missile|laser|explosion)\s+(?:successfully\s+)?destroys?\b|""" +
            """\bdestroys?\s+(?:barracks|satellite|missile|drone|aircraft|tank)\b|""" +
            """\b(?:hurricane|tornado|flood|floods|storm|earthquake|wildfire|cyclone|typhoon|tsunami|blizzard)\s+(?:[\w-]+\s+){0,2}(?:destroys?|ravages?|wipes?\s+out|flattens?|devastates?)(?:\s+(?:hundreds\s+of\s+|thousands\s+of\s+|scores\s+of\s+|dozens\s+of\s+)?(?:homes|houses|towns|villages|buildings|bridges|communities|acres|forests|crops|infrastructure))?\b|""" +
            """\b(?:destroys?|ravages?|flattens?|devastates?)\s+(?:hundreds\s+of\s+|thousands\s+of\s+|scores\s+of\s+|dozens\s+of\s+)?(?:homes|houses|towns|villages|buildings|bridges|communities|acres|forests|crops|infrastructure)\b|""" +
            """\b(?:workers?|unions?|nurses?|drivers?|rail\s+workers?|teachers?|staff|miners?|doctors?|crew)\s+(?:to\s+)?(?:strike|strikes|striking|walk\s+out)(?:\s+(?:at|in)\s+[\w-&]+)?(?:\s+(?:over|for|amid|after|against|across))?\b|""" +
            """\b(?:strike|strikes)\s+at\s+(?:Amazon|Boeing|ports?|refiner(?:y|ies)|plants?|hospitals?|schools?|depots?|factories|automakers?)\b|""" +
            """\b(?:plant|factory|refinery|depot|store|branch|rail|port|airport|mine|reactor|facility|pipeline|foundry)\s+shuts?\s+down\b|""" +
            """\bshuts?\s+down\b\s+(?:(?:a|an|the|its|his|her|their|[\w'-]+)\s+){0,3}(?:plant|factory|refinery|depot|store|branch|airport|runway|mine|reactor|facility|pipeline|""" +
            """spaces?|programm?es?|service|office|studio|lab|division|unit|project|hub|cent(?:er|re)|shop|restaurant|museum|school|clinic|app|website|site|servers?|platform|garage|team)\b|""" +
            """\b(?:birthday|wedding|anniversary|celebrity|holiday|charity|star-studded|Christmas|New\s+Year'?s?|gala|Oscars?|Halloween|launch|farewell|welcome|wrap)\s+bash\b|""" +
            """\bbash\s+for\s+(?:his\s+|her\s+|their\s+)?(?:birthday|anniversary|charity)\b|""" +
            """\b(?:plot|conspiracy|charged|guilty|convicted|sentenced|jailed)\s+(?:with|of|for|to)\s+(?:abuse|kill|murder|torture|assault)\b|""" +
            """\b(?:plot\s+to\s+abuse|abuse\s+of|abusing|murder\s+of|killing\s+of)\s+(?:a\s+)?(?:child|children|baby|infant|toddler)\b|""" +
            """\bbranded\s+on\b|""" +
            """\b(?:storms?|tornado(?:es)?|hurricanes?|blizzards?|typhoons?|cyclones?|gales?|winds?|rain|snow|heatwaves?|floods?|waves?)\s+(?:[\w-]+\s+)?slam(?:s|med|ming)?\b|""" +
            """\bslam(?:s|med|ming)?\s+(?:into|onto)\b|""" +
            """\b(?:the|a|that|this|those|these|my|your|his|her|their|our)\s+blasted\b|""" +
            """\balarming\s+(?:the|its|his|her|their|our|many|some)\b|""" +
            """\bbash(?:es|ed|ing)?\s+(?:a\s+|an\s+|his\s+|her\s+|their\s+)?(?:maiden\s+|quickfire\s+|rapid\s+|blistering\s+|unbeaten\s+)?(?:\d+\s+)?(?:ton|century|fifty|six(?:es)?|four(?:s)?|boundaries|boundary|homer|home\s+run|runs)\b""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * An unnamed-source credit at the end of the headline, wire style: "OpenAI,
     * Nvidia CEOs to attend Trump-Xi state dinner: Sources", "... -sources". It is
     * the same anonymous sourcing as "sources say", moved to the end. The space
     * required before a dash keeps "open-sources" out.
     */
    private val TRAILING_SOURCES_CREDIT = Regex(
        """(?:\s-\s*|:\s*)sources?(?:\s+(?:say|said|says|tell|told))?\s*[.!]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Known non-claim administrative nouns or standard terms that appear in quotes in UK journalism. */
    private val SCARE_QUOTE_EXCLUSIONS = setOf(
        "associate member", "boards", "middle powers", "triple lock",
        "neighbourhood watch", "the lads from footy", "lads from footy",
    )

    private val SECOND_PERSON_THREAT = Regex(
        """\b(?:your|our)\s+(?:savings|money|home|house|health|job|taxes|pension|diet|car|phone|kids|children|family|body|skin|waistline|flight|flights|commute|wallet|accounts|investments|data|privacy|routine)\b.*?\b(?:at\s+risk|in\s+danger|under\s+threat|harming|costing|listening|tracking|ruining|destroying|secretly\b|could\s+(?:be|cost|harm|ruin|drain|transform)|may\s+(?:be|cost|harm|ruin|drain)|might\s+(?:be|cost|harm|ruin|drain))\b""",
        RegexOption.IGNORE_CASE,
    )

    private val LEADING_EXCLAMATION_HOOK = Regex(
        """^\W*([A-Za-z0-9\s'’-]{2,30})[!]\s+[A-Z]"""
    )

    private val RHETORICAL_QUESTION_HOOK = Regex(
        """^\W*([A-Za-z0-9\s'’-]{2,35})[?]\s+[A-Z]"""
    )

    private val TEASER_PROFILE_LEAD = Regex(
        """^\W*Inside\s+(?:the\s+)?(?:[A-Z][\p{L}'.-]*|[a-z]+'s?)(?:\s+[\p{L}'.-]+){0,5}?'s?\s+(?:(?:\w+)\s+){0,2}(?:life|world|story|fall|rise|marriage|split|feud|secret|secrets|romance|love\s+story)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val CELEBRITY_REACTION_FORMULA = Regex(
        """\b(?:[A-Z][\p{L}'.-]*\s+){1,4}(?:Reacts|Addresses|Breaks\s+Silence|Speaks\s+Out)\s+(?:To\s+(?:(?:[A-Z][\p{L}'.-]*\s+){0,3})?(?:Sister|Brother|Mom|Dad|Husband|Wife|Ex\b|Dating|Romance|Split|Drama|Feud|Rumors|Rumours|Backlash|Claims|Being\s+Voted|Fans)|Fans\s+Who|Criticism|Dating\s+Rumors|Dating\s+Rumours)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Broadsheet demographic and lifestyle trend 'Why' teasers ('why couples are getting', 'why fans are digging'). */
    private val BROADSHEET_FEATURE_WHY = Regex(
        """\bwhy\s+(?:people|couples|men|women|voters|fans|drivers|shoppers|customers|readers)\s+are\s+\w+ing\b""",
        RegexOption.IGNORE_CASE,
    )


    /**
     * An answer announced and held back: somebody reveals, shares, explains or
     * exposes why, how or what ("Ben Affleck reveals why he cast ex-wife
     * Jennifer Garner", "Pete Davidson Shares Why Becoming a Dad Made His
     * Career Matter Less", "Scientists explain why cats purr", "Revealed: how a
     * father-of-two became…"). The headline sells the answer as a payoff it
     * has and won't give, so it is withheld detail even when it names the
     * subject (codebook v3 rule 5).
     *
     * A how-to stays a how-to ("Martin Lewis explains how to avoid the price
     * cap hike"), and the tease is off when the headline gives the answer
     * ([ANSWER_AFTER_COLON]).
     */
    private val TELL_ALL_WHY_HOOK = Regex(
        """\b(?:(?:reveal(?:s|ed)?|expose[sd]?|share[sd]?|explain(?:s|ed)?|unpack(?:s|ed)?|details?|detailed|tells|recalls?|recalled|recounts?|recounted)\s+(?:exactly\s+)?""" +
            """(?:why\b|how\b(?!\s+(?:to|you\s+can)\b)|what\b(?!\s+to\b)(?!(?:\s+[\w'-]+){1,3}\s+(?:needs?\s+to|should|must)\s+(?:know|do)\b)|(?:(?:the|a|her|his|their|its)\s+)?(?:real\s+|true\s+|['"][^'"]{1,25}['"]\s+)?reasons?\b)|""" +
            """revealed:?\s+(?:why|how|what)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The headline gives the answer it announced: a colon followed by an
     * unquoted clause of three or more words ("Glen Powell Explains Why He
     * Doesn't Preach Politics: It Divides People").
     *
     * A quotation after the colon does not count. It is the speaker's words,
     * which may not be the answer at all ("reveals why Biden's daughter
     * threatened to sue her: 'Bordered on parody'"). Nor does a quotation that
     * leads the headline, which often states the problem rather than the
     * answer ("'...I was convinced I would die': ... reveals how she learned to
     * cope").
     */
    private val ANSWER_AFTER_COLON = Regex("""^[^:]*:\s*(?!['"])[\p{L}\p{N}][\p{L}\p{N}'-]*(?:\s+[\p{L}\p{N}][\p{L}\p{N}'-]*){2}""")

    /**
     * Keeps an announced-answer match unless the answer follows it or the
     * words are a quoted person's, which are theirs rather than the outlet's
     * (codebook rule 1).
     */
    private fun announcedAnswerKept(headline: String, match: MatchResult): Boolean =
        !ANSWER_AFTER_COLON.containsMatchIn(headline.substring(match.range.last + 1)) &&
            QUOTED_SPEECH.findAll(headline).none { it.range.overlaps(match.range) }

    /**
     * Somebody said something and the headline holds back what: "breaks
     * silence", "speaks out", "reacts to" ("Lindsay Clancy's Husband and
     * Holdout Juror Break Their Silence", "Mauricio Umansky Reacts to
     * Stepdaughter's Pregnancy"). Codebook v2 counts these as withheld detail.
     *
     * Not counted:
     * - the headline says what was said: a colon after the phrase ("Pep
     *   Guardiola breaks his silence: Former Man City boss BACKS guilty
     *   club"), a speech verb followed by at least three words ("…and says
     *   'he scammed my heart'"), a stance ("speaks out against the war"), the
     *   reaction itself ("reacts to the verdict by quitting"), or a quotation
     *   that leads the headline ("'Difficult to say with a straight face':
     *   Tapper reacts to…"). "…and says 'I believe'" still counts: two words
     *   are a fragment, not what was said;
     * - speaking out or reacting in general rather than one statement
     *   ("afraid to speak out", "women who speak out", "lets offices react
     *   to", "How kids react to…"), or a call to action that opens the
     *   headline ("Speak out against hate");
     * - markets, which react by moving ("Stocks react to jobs report");
     * - quoted speech, which is the speaker's (codebook rule 1).
     */
    private val WITHHELD_STATEMENT = Regex(
        """\b(?:(?:breaks?|broke|breaking)\s+(?:(?:his|her|their|its|my|our)\s+)?silence|(?:speaks?|spoke)\s+out|(?:reacts?|reacted|reacting)\s+to)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val STATEMENT_GIVEN_AFTER = Regex(
        """:|^\s+(?:against|in\s+(?:favou?r|support|defen[cs]e)\s+of)\b|\bby\s+\p{L}+ing\b|""" +
            """\b(?:says?|said|tells?|told|insists?|admits?|vows?|claims?|calls?|warns?|urges?|denies|confirms?|declares?|thanks?|praises?|apologi[sz]es?|hits\s+back|slams?|blasts?)\b(?:\W+[\p{L}\p{N}]+){3}""",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING_QUOTE_COLON = Regex("""^\W*(?:'[^']{3,}'|"[^"]{3,}")\s*:""")
    private val GENERAL_NOT_ONE_STATEMENT = Regex(
        """(?:^\W*|\b(?:to|not|never|don't|didn't|doesn't|won't|will|would|could|can|can't|must|should|may|might|who|after|for|since|before|of|by|about|without)\s+|""" +
            """\b(?:let|lets|help|helps|make|makes|made)\s+(?:\S+\s+){0,2}|\bhow\s+(?:\S+\s+){0,4})$""",
        RegexOption.IGNORE_CASE,
    )
    private val MARKET_SUBJECT = Regex(
        """\b(?:markets?|stocks?|shares|bonds?|futures|prices|oil|gold|bitcoin|crypto|dollar|sterling|pound|euro|yen|investors|traders|street|ftse|dow|nasdaq|s&p(?:\s+500)?)\s+$""",
        RegexOption.IGNORE_CASE,
    )

    private fun withheldStatement(headline: String): List<MatchResult> {
        if (LEADING_QUOTE_COLON.containsMatchIn(headline)) return emptyList()
        val quoted = QUOTED_SPEECH.findAll(headline).map { it.range }.toList()
        return WITHHELD_STATEMENT.findAll(headline).filter { m ->
            val before = headline.substring(0, m.range.first)
            quoted.none { it.overlaps(m.range) } &&
                !GENERAL_NOT_ONE_STATEMENT.containsMatchIn(before) &&
                !MARKET_SUBJECT.containsMatchIn(before) &&
                !STATEMENT_GIVEN_AFTER.containsMatchIn(headline.substring(m.range.last + 1))
        }.toList()
    }

    private val SPECULATIVE_FINANCIAL_CATAPHORA = Regex(
        """\b(?:these|those)\s+(?:stocks|shares|lenders|funds|companies|investments)\s+(?:can|could|may|might|will|are\s+poised|are\s+set|to\s+buy)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val RHETORICAL_QUESTION_FULL = Regex(
        """^\W*(?:Has|Have|Did|Is|Are|Could|Can|Will)\s+[A-Za-z0-9\s'’-]+\s+(?:just|really|actually|secretly)\b.*?\?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val BETTERIDGE_SUPERLATIVE_QUESTION = Regex(
        """^\W*(?:Has|Have|Did|Is|Are|Could)\s+.*?\b(?:just|really|actually)\b.*?\b(?:world's|ever|first|lightest|fastest|best|worst|greatest|cheapest|smallest|biggest)\b.*?\?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A second sentence that says the first was only the beginning: "It's
     * Just the Tip of the Iceberg.", "Trucks, rails are only the start". The
     * subject can be any short phrase; the hook is the promise of more.
     */
    private val TWO_SENTENCE_CLIFFHANGER = Regex(
        """[.!?]\s+[\p{L}'-]+(?:,?\s+[\p{L}'-]+){0,4}?(?:'s|'re|\s+is|\s+are)\s+(?:just\s+)?(?:the\s+tip\s+of\s+the\s+iceberg|only\s+the\s+(?:start|beginning))\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A statement followed by a short rhetorical question that nudges the
     * reader into a debate: "... Should he?", "... Is it too little, too late?".
     *
     * Three things this has to get right, all found by the v20 audit:
     *
     * - **Dashes.** [HeadlineFlagger] turns en and em dashes into "-" before
     *   any signal runs, so a " - " separator is what arrives here. Matching
     *   the dash characters themselves meant they could never fire.
     * - **The question can run on.** "Don't they have more pressing issues?"
     *   and "are you feeling jealous, Vladimir?" continue past the lead-in, so
     *   up to 60 characters are allowed before the "?", commas included.
     *   A third-person subject may also carry a verb ("Should he worry?",
     *   codebook v7 leading question); "Should you…?" may not, since a
     *   review asking "Should you upgrade?" answers it.
     * - **The byline is not the question.** The match stops at "?". A trailing
     *   "| Author" is only checked with a lookahead, so the underline never
     *   covers the writer's name.
     *
     * Both lookbehinds are fixed-length, which Android's ICU regex requires.
     */
    private val TRAILING_RHETORICAL_QUESTION = Regex(
        """(?:(?<=[.!]\s)|(?<=\s-\s))""" +
            """(?:(?:Should|Could|Can|Will|Would)\s+(?:he|she|they)\b[^.!?|]{1,40}|""" +
            """(?:(?:Should|Could|Can|Will|Would)\s+(?:he|she|they|it|we|you)\b|""" +
            """Is\s+it\s+(?:too\s+little,?\s+)?too\s+late)\s*|""" +
            """(?:Don't\s+they\s+have|""" +
            """What\s+are\s+(?:the\s+[\w\s]+|they|we)\s+going\s+to\s+do|""" +
            """(?:So\s+)?why\s+can't\s+(?:the\s+[\w\s]+|they|we)|""" +
            """Why\s+do\s+we|""" +
            """But\s+can\s+(?:I|we)\s+get\s+it\s+back|""" +
            """Are\s+you\s+feeling)""" +
            """[^.!?|]{0,60})""" +
            """\?(?=\s*(?:\|.*)?$)""",
        RegexOption.IGNORE_CASE,
    )


    /**
     * A named person cast as attacking another. "Launches attack on" is not
     * here: it is also how wire copy reports a military strike ("Russia
     * launches fresh attack on Kyiv"), which is a real armed clash, not
     * conflict framing (codebook §4).
     */
    private val CONFLICT_STAGING = Regex(
        """\b(?:[A-Z][\p{L}'.-]*(?:\s+[A-Z][\p{L}'.-]*){0,3})\s+(?:fires\s+back\s+at|hits\s+out\s+at|breaks\s+ranks\s+with|doubles\s+down\s+on\s+(?:anti-[\w]+|rhetoric|attacks?|claims?|criticism|feud|insults?|threats?))\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Somebody's words staged as a fight: a person responds to, hits back at
     * or addresses another person's comments, remarks or criticism ("Lily
     * Allen responds to David Harbour's comments about her break-up album"),
     * or answers a hostile crowd ("claps back at trolls"). Codebook v3 counts
     * it as conflict framing: the exchange of words is made the story.
     *
     * The object has to be somebody's words or an adversarial crowd, so an
     * institution responding to an event never matches ("Police respond to
     * shooting", "Council responds to petition", "Government responds to
     * criticism").
     */
    private val STAGED_EXCHANGE = Regex(
        """\b(?i:(?:respond(?:s|ed|ing)?|repl(?:y|ies|ied|ying)|react(?:s|ed|ing)?)\s+to|(?:hits?|hitting|fires?|fired|firing|claps?|clapped|clapping)\s+back\s+at|address(?:es|ed|ing)?)\s+""" +
            """(?:(?:[A-Z][\p{L}'.-]*\s+){0,3}[A-Z][\p{L}.-]*(?:'s|')\s+(?:[\p{L}-]+\s+){0,2}?""" +
            """(?i:comments?|remarks?|criticism|claims?|jibes?|digs?|swipes?|attacks?|insults?|accusations?|posts?|tweets?|rants?|outbursts?|words)|""" +
            """(?i:(?:(?:his|her|their|the)\s+)?(?:critics|haters|trolls|detractors)))\b""",
    )

    /** Prescribed emotional commands and visceral body promises common in social clickbait. */
    private val VIRAL_EMOTIONAL_IMPERATIVE = Regex(
        """\b(?:(?:that\s+)?will\s+make\s+you\s+(?:cry|laugh|smile|scream|wince|cringe|lose\s+your\s+mind|rethink|want\s+to|laugh\s+out\s+loud)|will\s+make\s+your\s+(?:jaw\s+drop|mouth\s+water|blood\s+boil|skin\s+crawl|heart\s+melt|day)|will\s+give\s+you\s+(?:life|nightmares|chills|all\s+the\s+feels)|will\s+(?:blow\s+your\s+mind|restore\s+your\s+faith|leave\s+you\s+speechless|break\s+your\s+heart|surprise\s+you|warm\s+your\s+heart)|changed\s+our\s+lives|so\s+cute\s+it\s+hurts)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** In-group identity gating formula ('Only 90s Kids Will Understand', 'You'll Only Relate To If'). */
    private val INGROUP_IDENTITY_HOOK = Regex(
        """\b(?:only\s+[A-Za-z0-9'\s-]{2,25}\s+will\s+(?:understand|relate\s+to|get|appreciate|remember|recognize)|will\s+speak\s+to\s+anyone\s+who|you'll\s+only\s+relate\s+to\s+if)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Forward-reference lead pointing to unstated content ('This Is
     * the Story of How...', 'Here Are The Top'). "This is how/what/why" and "Here
     * is what/why/how" belong to [WITHHELD_ANSWER_HERE], which leaves service
     * phrasing alone: "This Is How to Stop Them" is a how-to (codebook rule 4).
     */
    private val CATAPHORIC_DEICTIC_LEAD = Regex(
        """^\W*(?:this\s+is\s+the\s+story\s+of\s+how|here\s+are\s+the\s+(?:top|trending))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Personality quiz and self-identification formulas ('Which Character Are You', 'Which Song Matches Your Birth Month', 'Are You More X Or Y'). */
    private val QUIZ_SELF_ID_FORMULA = Regex(
        """^(?:which\s+.*?\b(?:are\s+you|should\s+(?:you|be)|matches\s+your|is\s+your\s+soulmate|is\s+actually\s+the\s+best|would\s+you)\b|how\s+much\s+of\s+a\s+[A-Za-z0-9'\s-]{2,25}\s+are\s+you\b|how\s+[A-Za-z-]{3,15}\s+are\s+you(?:\s+(?:really|actually|today|on\s+a\s+scale|[?]))?\s*${'$'}|are\s+you\s+(?:more\s+.*?\s+or\s+|a\s+(?:true|real|proper|lot|brave)\b|smart\s+enough|guilty\s+of|ready\s+for))""",
        RegexOption.IGNORE_CASE,
    )

    /** Second-person prescriptive commands ('You Need To See', 'You Won't Be Able To Stop Watching'). */
    private val VIRAL_PRESCRIPTIVE_COMMAND = Regex(
        """\b(?:you\s+need\s+to\s+(?:see|hear|try|watch)|you\s+have\s+to\s+(?:see|hear|watch|try)|you\s+won't\s+be\s+able\s+to\s+stop\s+watching)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Listicle format with relative clauses, second-person pronouns, or narrative subjects, allowing 0-2 adjectives. */
    private val VIRAL_LISTICLE_HOOK = Regex(
        """^\d+\s+(?:(?:[A-Za-z-]+\s+){0,2})?(?!ways\s+to\b)(?:times|things|reasons|ways|products|photos|pictures|tweets|posts|confessions|facts|recipes|secrets|charts|hacks|lessons|signs|gifs|illustrations|moments|jokes|outfits|items|tricks|comebacks|decorations|memes|quotes|mistakes|errors|habits)\s+(?:[A-Z][\p{L}'.-]*|that|you|to|every|only|who|will|we|from|in|about|wish|sum\s+up|are\b|everyone|nobody|never)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Prediction / profiling quiz formula ('We Know Which X Based On Your Y', 'We Know What Your X Is'). */
    private val WE_KNOW_BASED_ON = Regex(
        """\bwe\s+know\s+(?:which|what|who|if|how|your)\b.*?(?:\bbased\s+on\b|\bis\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Direct challenge quizzes ('Can You Name These X', 'Can You Match The', 'We Bet You Can't'). */
    private val CHALLENGE_QUIZ_PROMPT = Regex(
        """^(?:can\s+(?:you|we)\s+(?:name|identify|guess|tell|spot|pass|solve|find|figure\s+out|match|pick|score|beat|survive|get\s+through|ace)|how\s+many\s+of\s+.*?\s+(?:have\s+you|do\s+you|can\s+you)|we\s+bet\s+you\s+can't)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Memory/knowledge test quizzes ('How Well Do You Know / Remember / See'). */
    private val HOW_WELL_DO_YOU = Regex(
        """^how\s+well\s+do\s+you\s+(?:know|remember|understand|recognize|see)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Hypothetical identity quiz ('What Would Your X Be', 'What Should Your Y Be'). */
    private val WHAT_WOULD_YOUR_BE = Regex(
        """^what\s+(?:would|should)\s+your\s+[A-Za-z0-9'\s-]{2,25}\s+be\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Ranked listicle superlative formula ('The 23 Absolute Worst...', '42 Of The Most Ridiculous...'). */
    private val SUPERLATIVE_LISTICLE_LEAD = Regex(
        """^(?:the\s+)?\d+\s+of\s+the\s+(?:absolute\s+worst|worst|craziest|weirdest|wildest|most\s+ridiculous|funniest|cutest|most\s+bizarre)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Narrative cliffhanger consequences ('...And Here's What Happened', '...And It Did Not Go Well', '...And It's Totally WTF'). */
    private val CLIFFHANGER_CONSEQUENCE = Regex(
        """\band\s+(?:broke\s+down\s+in\s+tears|here's\s+what\s+happened|it\s+did\s+not\s+go\s+well|it\s+was\s+(?:amazing|impressive|terrifying|hilarious|horrifying|exhausting)|the\s+results\s+are\b|(?:it's|it\s+is)\s+(?:pretty|actually|totally|completely|so|absolutely)\s+[A-Za-z]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Second-person viral hooks ('Hacks You Should Try', 'You Never Knew Existed', 'You Didn't Know You Needed'). */
    private val SECOND_PERSON_VIRAL_HACKS = Regex(
        """\b(?:you\s+should\s+(?:try|be|check\s+out)|you\s+never\s+knew\s+(?:there\s+were|existed|about|you\s+needed)|you'll\s+ever\s+need|you'll\s+actually\s+want\s+to\s+try|you\s+didn't\s+know\s+you\s+needed|you\s+might\s+not\s+know\s+(?:are|is|about)|that\s+will\s+make\s+you\s+feel\s+old|that\s+will\s+make\s+your\s+heart\s+(?:skip|melt|break|ache))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Deictic forward-referencing viral subject ('This Guy Filmed A Music Video', 'This Mom Hilariously'). */
    private val DEICTIC_VIRAL_SUBJECT = Regex(
        """^this\s+(?:guy|woman|man|mom|dad|girl|boy|dog|cat|toddler|teen|teacher|waiter|grandma|grandpa|couple|family)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** First-person collective viral editorial leads ('We Need To Talk About', 'We Bet You Can't'). Excludes review journalism ("We tested"). */
    private val VIRAL_FIRST_PERSON_LEAD = Regex(
        """^we\s+(?:need\s+to\s+(?:talk\s+about|stop|start)|bet\s+you\s+can't|can't\s+stop\s+looking)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Viral reaction superlative formula ('Had The Perfect Response To', 'Gave The Perfect Response'). */
    private val PERFECT_RESPONSE_FORMULA = Regex(
        """\b(?:had|has|gives?|gave)\s+the\s+perfect\s+response\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Elliptical conversational meme frame ('When You Just Can't', 'When Your BF Has A Female BFF'). */
    private val VIRAL_MEME_FRAME = Regex(
        """^when\s+(?:you|your)\s+(?:just\s+)?(?:can't|don't|realize|find\s+out|see|have|try|get|are|look|walk|tell|hear|know|is|wants?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Combat sports matchups and boxing proper nouns ('Fury-Joshua', 'Tyson Fury', 'Fury vs Usyk'),
     * preventing sports coverage from triggering manufactured rage.
     */
    private val SPORTS_FURY = Regex(
        """\b(?:Tyson\s+Fury|Tommy\s+Fury|John\s+Fury|Hughie\s+Fury|Fury-(?:Joshua|Usyk|Wilder|Paul|Ngannou|Chisora|Whyte|Ruiz)|(?:Joshua|Usyk|Wilder|Paul|Ngannou|Chisora|Whyte)-Fury|Fury\s+(?:vs\.?|v\b|bout|fight|rematch|showdown|clash))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Accusatory habit or mistake formulas ('5 Sunscreen Mistakes You're Probably Making', 'Habits You're Making'). */
    private val ACCUSATORY_HABIT_LISTICLE = Regex(
        """\b(?:mistakes|errors|habits|things|foods|drinks|products)\s+(?:you're|you\s+are)\s+(?:probably\s+)?(?:making|doing|eating|drinking|buying|overlooking|getting\s+wrong)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Withheld discovery and science curiosity gap formulas ("Einstein Called It 'Spooky.' CERN Just Found It"). */
    private val WITHHELD_DISCOVERY_HOOK = Regex(
        """\b(?:called\s+it\s+['"“][^'"”]+['"”][.?!]\s+.*?\b(?:just\s+)?(?:found|discovered|detected)|(?:scientists|researchers|cern|nasa|astronomers|physicists)\s+just\s+(?:found|discovered|detected|proved|solved|unveiled)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Imperative reaction video hooks ('Hear Panelist's Reaction', 'Watch Anchor's Reaction'). */
    private val IMPERATIVE_REACTION_HOOK = Regex(
        """\b(?:Hear|Watch|See)\s+[\w'.-]{2,30}'s?\s+reaction\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "Here's why/how/what..." and "This is why/how/what...": the headline
     * points at an answer it has and holds it back ("Here's why the pound is
     * falling", "Here's how much Snapchat is now valued at", "This is what
     * happens when you swallow your gum", "Here's what we know"). Codebook v3
     * rule 5 counts an announced answer as withheld detail even when the
     * subject is named.
     *
     * Service phrasing is left alone, because it promises instructions rather
     * than dangling a payoff: "Here's how to see it", "Here's what to buy",
     * "Here's what you need to know", "Here's how you can watch". A plain
     * question or topic ("Why the pound is falling") never reaches this
     * signal, and the tease is off when the headline gives the answer
     * ([ANSWER_AFTER_COLON]). "Here's what you need to know" tacked onto a
     * news event is a tease, caught by [PRESCRIPTIVE_DEFICIT_HOOK].
     */
    private val WITHHELD_ANSWER_HERE = Regex(
        """\b(?:here(?:'s|s|\s+is)|this\s+is(?:\s+exactly|\s+precisely)?)\s+(?:""" +
            """why\b|""" +
            """how\b(?!\s+to\b)|""" +
            """what\b(?!\s+to\b)(?!(?:\s+[\w'-]+){1,3}\s+(?:needs?\s+to|should|must)\s+(?:know|do)\b)|""" +
            """the\s+(?:real|surprising|simple|one|only|secret|shocking|weird|strange)\s+(?:reason|thing|trick|way|secret|answer|truth|cause)\b""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Trailing prescriptive deficit hook ("... - here's what you need to know",
     * "... — here's what you need to know", "... . Here's what you need to know",
     * "...: here's what you need to know"). Tacking a deficit tease onto an
     * ordinary story announces an answer and holds it back (codebook rule 5).
     * It must close the headline: one that opens with it, or goes on to name
     * its subject, is a service guide and is left alone ("Here's what you need
     * to know if you haven't filed your return", "... Here's what you need to
     * know about the condition").
     */
    private val PRESCRIPTIVE_DEFICIT_HOOK = Regex(
        """(?:(?<=[.!]\s)|(?<=\s[-–—]\s)|(?<=:\s))\bhere(?:'s|s|\s+is)\s+what\s+you\s+need\s+to\s+know\b(?=\s*[.!]?\s*(?:\|.*)?$)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A reader's dilemma closed by an unresolved verdict question ("My sister
     * ... says she deserves the family home. Is she right?", "My adviser wants
     * me to invest $350,000 ... Is this risky?"): the columnist's verdict is
     * the payoff held back (codebook rule 5). The dilemma must be told in the
     * first person ([FIRST_PERSON_BEFORE]), so an explainer question ("Covid
     * jab for under-5s: is it safe?") is left alone.
     */
    private val ADVICE_DILEMMA_CLIFFHANGER = Regex(
        """(?:(?<=[.!]\s)|(?<=\s[-–—]\s)|(?<=:\s))""" +
            """(?:Is\s+(?:this|that|it|he|she|they)\s+(?:risky|right|wrong|fair|legal|normal|true|possible|safe|worth\s+it)|""" +
            """What\s+should\s+I\s+do|Should\s+I\s+(?:do\s+it|worry|sell|buy|walk\s+away))""" +
            """\?(?=\s*(?:\|.*)?$)""",
        RegexOption.IGNORE_CASE,
    )

    /** First-person words, case-aware so "US" (the country) is not "us". */
    private val FIRST_PERSON_BEFORE = Regex("""\b(?:I|I'm|I've|[Mm]y|[Mm]e|[Ww]e|[Oo]ur)\b""")

    /**
     * "This" or "these" pointing at something the headline never names: the
     * codebook's own definition of withheld detail. Examples: "This
     * semiconductor stock is in the dumps", "If you don't like using Siri,
     * this iOS 27 feature may change your mind", "Mount Vesuvius Buried These
     * Scrolls for 2,000 Years".
     *
     * Scott (2021, Journal of Pragmatics 175) finds definite referring
     * expressions significantly over-represented in clickbait headlines; a
     * demonstrative with nothing to point back to is the plainest case. On the
     * project's own modern data, "this" or "these" appears in 792 of 3,544
     * Webis-Clickbait-22 tuning posts (22%) but in 47 of 2,567 tuning
     * headlines from 2026 that are labeled NONE (2%).
     *
     * Not counted:
     * - time ("this week", "this wildfire season");
     * - part of a title ("she belts out Fight For This Love");
     * - a demonstrative that starts a copula ("This is...", "These are..."),
     *   which other signals handle;
     * - pointing back ("this latest crisis", "this type of vacation");
     * - a service list ("These stylist-approved tips"), or a sale ("Save more
     *   than $100 off this popular Dyson vacuum");
     * - quoted speech, which is the speaker's (codebook rule 1);
     * - signed or labeled opinion, where "this" points at the writer's own
     *   argument ("this speech", "this good idea"). Every such case in the
     *   tuning split of broadsheet-2026 is labeled NONE.
     *
     * The underline stops at the first verb or preposition, so it marks the
     * unnamed thing ("These stocks"), not the claim about it.
     */
    private val DEMONSTRATIVE_PHRASE = Regex(
        """(?<![\p{L}\p{N}'])(?:this|these)((?:\s+[\p{L}\p{N}$£€][\p{L}\p{N}$£€'.,-]*){1,3})""",
        RegexOption.IGNORE_CASE,
    )
    private val PRICE = Regex("""[$£€]\s?\d""")

    /**
     * Words that turn a price into a sale. A price alone is not enough: "This
     * Woman Paid Over $10,000 To Look Like A Disney Princess" is a tease, and 22
     * of the 23 Webis-Clickbait-22 tuning posts with a price and a
     * demonstrative have no sales word.
     */
    private val SALES_CUE = Regex(
        """\b(?:save|off|sale|deals?|discount|under|starting|start\s+at|reduced|bargain|for\s+just|for\s+only)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val NON_SPACE_RUN = Regex("""\S+""")

    /** Words that end the unnamed thing's phrase: verbs, modals and prepositions. */
    private val PHRASE_STOP = setOf(
        "is", "are", "was", "were", "isn't", "aren't", "wasn't", "weren't", "be", "been",
        "will", "would", "could", "can", "may", "might", "should", "must", "won't", "can't",
        "has", "have", "had", "does", "did", "do", "to", "of", "in", "on", "at", "for", "with",
        "from", "by", "into", "that", "which", "who", "and", "or", "but", "as", "about",
    )

    /** A first word after this/these that means the demonstrative is not pointing at a hidden thing. */
    private val NOT_A_HIDDEN_REFERENT = PHRASE_STOP + setOf(
        "a", "an", "the", "it", "he", "she", "they", "we", "you", "i", "means", "latest",
        "same", "type", "kind", "sort", "very", "particular", "much", "many", "all", "too",
        "so", "just", "again", "also", "here", "there", "now", "way",
        "world", "planet", "earth", "place", "point", "stage", "moment", "age", "generation",
        "life", "side", "election", "government", "case",
        // Rule pack 30: the thing is the graphic itself, which the story shows.
        "chart", "charts", "map", "maps", "graph", "graphs", "graphic", "timeline",
    )

    /**
     * A sentence already ended earlier in the headline, and the demonstrative
     * is not the first word of its own sentence: it points back at what that
     * sentence named ("I made Ina Garten's mashed potatoes. From now on, I'll
     * bring this easy side dish…"). One opening a sentence still points
     * forward ("Doctors hate him. This one trick…"). Rule pack 32, from the
     * 2026-10 flag review.
     */
    private fun pointsBack(headline: String, at: Int): Boolean {
        val before = headline.substring(0, at)
        val lastBreak = SENTENCE_BREAK.findAll(before).lastOrNull() ?: return false
        return before.substring(lastBreak.range.last + 1).isNotBlank()
    }
    private val SENTENCE_BREAK = Regex("""[.!?]\s+(?=\p{Lu})""")

    /** "these 5 lesser-known locations": a count opens a list, which points forward. */
    private val COUNT_WORD = Regex("""\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|few|several|dozen""")

    /** Time words anywhere in the phrase: "this week", "this coming weekend", "this wildfire season". */
    private val TIME_WORDS = setOf(
        "year", "years", "week", "weeks", "weekend", "weekends", "month", "months", "season",
        "seasons", "summer", "winter", "spring", "autumn", "fall", "morning", "afternoon", "evening",
        "night", "tonight", "today", "time", "times", "day", "days", "christmas", "easter",
        "halloween", "thanksgiving", "hanukkah", "diwali", "ramadan", "eid", "passover",
        "holiday", "holidays", "decade", "century", "era", "quarter", "term", "break",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "june", "july", "august", "september",
        "october", "november", "december",
    )

    /** Service lists, where the category is the subject and the items are the service. */
    private val SERVICE_LIST_WORDS = setOf(
        "tips", "picks", "finds", "deals", "buys", "gifts", "ideas", "recipes", "products",
        "styles", "outfits", "pairs", "items", "essentials", "options", "brands", "hacks",
    )

    /** True when most longer words are capitalized, so capitals say nothing about names or titles. */
    private fun isTitleCase(headline: String): Boolean {
        val words = WORD.findAll(headline).map { it.value }.filter { it.length >= 4 }.toList()
        return words.isNotEmpty() && words.count { it.first().isUpperCase() } >= words.size * 0.8
    }

    private val WORD = Regex("""\p{L}[\p{L}'-]*""")
    /** A proper name of one to four capitalized words, then a colon, opening the headline. */
    private val NAME_THEN_COLON = Regex("""^\W*\p{Lu}[\p{L}'’.-]*(?:\s+\p{Lu}[\p{L}'’.-]*){0,3}\s*:""")
    private val SENTENCE_START = Regex("""(?:^|[.!?:]\s+|\s-\s|['"]\s*)$""")

    private fun unnamedDemonstrative(headline: String, articleType: ArticleType? = null): List<MatchResult> {
        if (labelledOpinion(headline, articleType).isNotEmpty()) return emptyList()
        // Rule pack 30: "Île Seguin: This tiny island just outside Paris…" names it first.
        val namedBeforeColon = NAME_THEN_COLON.find(headline)?.range?.last ?: -1
        val quoted = QUOTED_SPEECH.findAll(headline).map { it.range }.toList()
        val titleCase = isTitleCase(headline)
        return DEMONSTRATIVE_PHRASE.findAll(headline).mapNotNull { m ->
            val words = m.groupValues[1].trim().split(WHITESPACE)
            val norm = words.map { it.lowercase().trimEnd('.', ',', ';', ':', '?', '!').removeSuffix("'s") }
            val stopAt = norm.indexOfFirst { it in PHRASE_STOP }.let { if (it < 0) norm.size else it }
            val punctuated = words.indexOfFirst { it.last() in ".,;:?!" }.let { if (it < 0) norm.size else it + 1 }
            val phraseLength = minOf(stopAt, punctuated)
            // A capital "This" in the middle of a sentence-case headline is part
            // of a title: "she belts out Fight For This Love".
            val midSentenceCapital = !titleCase && headline[m.range.first].isUpperCase() &&
                !SENTENCE_START.containsMatchIn(headline.substring(0, m.range.first))
            // In a sentence-case headline a capitalized word inside the phrase is a
            // name, so the referent is named: "these Silicon Valley haunts".
            // (Title Case gives no such clue.) Rule pack 28, from
            // reported false flags.
            val namedInside = !titleCase && words.take(phraseLength).any { it.first().isUpperCase() }
            when {
                m.range.first > namedBeforeColon && namedBeforeColon >= 0 && m.range.first - namedBeforeColon <= 2 -> null
                phraseLength == 0 || norm.first() in NOT_A_HIDDEN_REFERENT -> null
                midSentenceCapital -> null
                namedInside -> null
                norm.take(phraseLength).any { it in TIME_WORDS } -> null
                pointsBack(headline, m.range.first) && !COUNT_WORD.matches(norm.first()) -> null
                norm.take(2).any { it in SERVICE_LIST_WORDS } -> null
                quoted.any { it.overlaps(m.range) } -> null
                PRICE.containsMatchIn(headline.removeRange(m.range)) && SALES_CUE.containsMatchIn(headline) -> null
                else -> {
                    val group = m.groups[1] ?: return@mapNotNull null
                    val lastWord = NON_SPACE_RUN.findAll(group.value).elementAt(phraseLength - 1)
                    var end = group.range.first + lastWord.range.last
                    while (end > m.range.first && headline[end] in ".,;:?!") end--
                    SpanMatchResult(m.range.first..end, headline.substring(m.range.first, end + 1))
                }
            }
        }.toList()
    }

    /** Unspecified threshold ('Reaches This Level', 'Hits This Mark'). */
    private val CATAPHORIC_THRESHOLD = Regex(
        """\b(?:reach(?:es)?|hits?|attains?|climbs?\s+to|drops?\s+to)\s+this\s+(?:level|number|threshold|mark|point)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A feeling word that tells the reader how to feel about an event or its
     * footage: "Terrifying footage shows...", "Horrifying moment girl, 14, hit
     * by car", "harrowing ordeal", "horror crash". Codebook v3 rule 3 counts it
     * as alarmist framing even when the event is real and filmed: the footage
     * proves the event, not the feeling. Only fear and horror count here; a sad
     * word ("heartbreaking death") is not alarm.
     *
     * "Footage shows jet nosediving" is plain description and has nothing for
     * this to match, and "horror film" or "horror game" is a genre, not a
     * feeling about an event. A witness's feeling ("horrified children look
     * on") is a different question and is not decided yet.
     */
    private val FEELING_WORD_EVENT = Regex(
        """\b(?:terrifying|horrifying|chilling|sickening|harrowing|horrific|shocking|disturbing|heart-?stopping|horror)\s+""" +
            """(?:[\w-]+\s+){0,3}?""" +
            """(?:moment|footage|video(?!\s+games?)|clip|scene|image|picture|photo|ordeal|attack|stabbing|shooting|crash|""" +
            """smash|collision|accident|incident|fall|plunge|collapse|fire|blaze|explosion|death|injur(?:y|ie)|""" +
            """discovery|find|account|testimony)(?:e?s)?\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Pseudo-reassurance alarmism: reassuring, calming, or negating phrasing wrapped
     * round a catastrophic hazard or deadly pathogen ("You Probably Aren't Going to Get
     * the Plague", "Why you shouldn't panic about bird flu", "No, you won't die from
     * flesh-eating bacteria", "Relax, an asteroid probably won't wipe out Earth").
     *
     * A project heuristic. None of the studies MLE cites tests this construction, and
     * it has not matched a headline in any benchmark tuning split.
     */
    private val PSEUDO_REASSURANCE_ALARM = Regex(
        """\b(?:(?:you|we)\s+probably\s+(?:aren't|won't)|no,\s+(?:you|we)\s+won't|(?:why\s+(?:you|we)\s+shouldn't|don't|no\s+need\s+to|nobody|no\s+one\s+should)\s+panic(?:\s+(?:about|over))?|relax[,:]\s+(?:an?|the|you|we)|calm\s+down[,:])\b.*?\b(?:plagues?|superbugs?|bird\s+flu|flesh-eating(?:\s+bacteria)?|ebola|anthrax|rabies|marburg|mpox|monkeypox|radiation|nuclear\s+war|nuclear\s+strike|asteroid|extinction|apocalypse|deadly\s+virus|kill\s+(?:you|us)|die\s+from)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Temporal threat suspension: surface reassurance that defers a catastrophe with a
     * trailing "for now" or "just yet" ("The electric grid won't collapse this winter
     * (for now)", "Your savings aren't gone just yet").
     *
     * A project heuristic. None of the studies MLE cites tests this construction, and
     * it has not matched a headline in any benchmark tuning split.
     */
    private val TEMPORAL_SUSPENSION_THREAT = Regex(
        """\b(?:won't|isn't|aren't|hasn't|not)\b.*?\b(?:collapse|crash|wiped?\s+out|kill|die|crisis|danger|recession|panic|extinct|fired|replaced|gone|lost|safe|doomed)\b.*?\b(?:just\s+yet|for\s+now|\.\.\.\s*yet)[.)]?\s*(?:\|.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Survival challenge framing: staging ordinary seasonal, financial, or lifestyle
     * events as life-or-death tests ("Can your body survive the summer heatwave?",
     * "Will your savings survive the autumn budget?", "Can you survive 30 seconds...").
     *
     * A project heuristic. None of the studies MLE cites tests this construction. It
     * has matched one legacy (2016) headline and nothing in the 2026 data or
     * Webis-Clickbait-22.
     */
    private val SURVIVAL_CHALLENGE_FRAME = Regex(
        """\b(?:can|will)\s+(?:you|your|we|our)\s+(?:body|savings|pension|wallet|business|job|marriage|dog|cat|plants?|car)\s+survive\s+(?:the\s+|this\s+|an?\s+)?(?:[A-Za-z-]{3,15}\s+)?(?:summer|winter|heatwave|cold\s+snap|budget|election|rate\s+hikes?|crash|turmoil|recession|inflation|cuts?)\b|""" +
            """\bcan\s+(?:you|we)\s+survive\s+\d+\s+(?:seconds|minutes|hours)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Flat order or warning telling the reader to abandon or adopt an
     * everyday habit without giving the reason ("Why you should never charge your
     * phone overnight", "The 1 food you should never eat", "Why you should think twice
     * before drinking tap water").
     *
     * A direct appeal in the tactic taxonomy of Nofar et al. (2025). It matches 11
     * Webis-Clickbait-22 tuning posts and no headline labeled NONE in the 2026 tuning
     * data.
     */
    private val PRESCRIPTIVE_BEHAVIORAL_IMPERATIVE = Regex(
        """\b(?:why\s+you\s+should\s+(?:never|always|stop|rethink|start)\s+[a-z]{3,20}|""" +
            """(?:the\s+)?(?:one\s+|\d+\s+)?(?:foods?|drinks?|habits?|mistakes?|things?)\s+you\s+should\s+(?:never|always|stop)|""" +
            """you\s+(?:must|should)\s+stop\s+(?:eating|drinking|buying|using|doing)\s+this|""" +
            """why\s+you\s+should\s+think\s+twice\s+before|""" +
            """don'?t\s+be\s+fooled)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Second-person tease ("you") paired with a verb of not knowing or missing
     * ("Things you probably didn't know", "The detail you
     * probably missed", "You probably forgot about these 90s shows").
     *
     * Forward reference (Blom & Hansen 2015) combined with a direct appeal (Nofar et
     * al. 2025). It matches four Webis-Clickbait-22 tuning posts, one harvested headline
     * labeled prominent, and no headline labeled NONE in the 2026 tuning data.
     */
    private val CATAPHORIC_SECOND_PERSON_GAP = Regex(
        """\b(?:you\s+probably\s+(?:(?:didn't|don't|never)(?!\s+(?:have|own|play|need|want|use|like|eat|drink|watch)\b)|missed|forgot|won't\s+have|already\s+forgot|qualify\s+to|definitely\s+have|shouldn't\s+try)|""" +
            """(?:things?|facts?|details?|products?|jokes?|traditions?|hacks?)\s+you\s+probably\b)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Recognized genre and section prefix anchors (review, preview, hands-on, impressions, opinion, analysis, column, editorial).
     */
    private val GENRE_PREFIX_SEPARATOR = Regex(
        """^(?:[A-Za-z0-9'’\s-:,]{1,60}\s+)?(?:reviews?|previews?|hands-on|impressions?|opinions?|op-eds?|analysis|columns?|editorials?|investigations?|investigative|locals?|perspectives?|guest essays?)\s*[:—–-]\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The headline tells the reader it is somebody's view. Codebook rule 2:
     * signed or labeled opinion is never opinion as fact, because the reader
     * has already been told whose judgment it is. Only opinion_as_fact uses
     * this; a column's alarm or tease is still judged normally.
     *
     * - Bylines: "SARAH VINE: ...", "...: MAUREEN CALLAHAN", "... | Jonathan
     *   Wilson", "..., reveals ex-anchor MELISSA FRANCIS".
     * - Labels: "Opinion:", "Op-Ed:", "Column:", "Comment:", "Letters", "Elden
     *   Ring review: ...", "The Guardian view on ...".
     * - The article's own type, from its feed or link: opinion, editorial,
     *   review or letters ([ArticleType.isSignedView]).
     *
     * "Analysis" is deliberately absent. The codebook lists opinion, editorial,
     * review and letters, and an analysis piece speaks in the outlet's voice.
     *
     * Measured against broadsheet-2026, where no signed or labeled headline
     * carries opinion_as_fact, and "Sham City: verdict ... is damning | Jonathan
     * Wilson" is NONE.
     */
    private val LEADING_CAPS_BYLINE = Regex("""^\W*((?:DR\s+)?[A-Z][A-Z'.-]+(?:\s+[A-Z][A-Z'.-]+){0,3})\s*:""")
    private val TRAILING_CAPS_BYLINE = Regex(
        """:\s*(?:[\p{L}'-]+\s+){0,4}?((?:DR\s+)?[A-Z]{2,}[A-Z'.-]*(?:\s+[A-Z][A-Z'.-]+){0,3})\s*$""",
    )
    private val TRAILING_SECTION = Regex("""\|\s*[^|\s][^|]{0,60}$""")
    private val TRAILING_SIGNOFF = Regex(
        """,\s+(?:reveals?|writes?|says?|claims?|argues?)\s+(?:[\p{L}'-]+\s+){0,5}?[A-Z][\p{L}'.-]*(?:\s+[A-Z][\p{L}'.-]*){0,3}\s*$""",
    )
    private val OPINION_LABEL = Regex(
        """^\W*(?:opinion|op-eds?|columns?|comment|editorials?|letters?|perspectives?|guest\s+essays?)\s*[:|-]|""" +
            """(?:^|\s)reviews?\s*[:-]\s|""" +
            """^\W*the\s+(?:[A-Z][\p{L}']*\s+){1,3}view\s+on\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Capitalized words that open or close a headline as a format label, not as somebody's name. */
    private val NOT_A_BYLINE = setOf(
        "WATCH", "LIVE", "BREAKING", "NEWS", "EXCLUSIVE", "REVEALED", "UPDATE", "UPDATED", "VIDEO",
        "PICTURED", "PICTURES", "PHOTOS", "JUST", "IN", "REPORT", "REPORTS", "POLL", "POLLS",
        "ANALYSIS", "EXPLAINER", "FACT", "CHECK", "LATEST", "URGENT", "ALERT", "WARNING", "RECAP",
        "SEE", "IT", "LISTEN", "PODCAST", "QUIZ", "GALLERY", "SPORT", "WEATHER", "TRAFFIC",
    )

    private fun looksLikeByline(name: String): Boolean {
        val words = name.trim().split(WHITESPACE)
        if (words.any { it in NOT_A_BYLINE || it in NON_BYLINE_WORDS || it in TABLOID_SHOUT_WORDS }) return false
        return !words.all { it in STANDARD_ACRONYMS }
    }

    /** The BBC's closing credit for a quote: "Disgraceful nothing has changed after Tarling death - Brennan". The words are a named person's, so they are not opinion passed off as fact (precision gap, 2026-11-12). */
    private val TRAILING_NAME_CREDIT = Regex("""\s[-–]\s(?!The\b|Reports?\b|Sources?\b|Poll\b|Study\b|Live\b)\p{Lu}[\p{L}'’]+(?:\s\p{Lu}[\p{L}'’]+)?$""")

    /** A view the writer owns in the first words: "I enjoy how…", "I think…" (precision gap, 2026-11-12). */
    private val FIRST_PERSON_VIEW = Regex(
        """^I\s+(?:really\s+|still\s+|kind\s+of\s+)?(?:enjoy|love|like|hate|think|loved|liked|hated|adore|can't\s+stand|don't\s+(?:like|think|care))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** The "secret" is named in the same breath, so nothing is held back: "This Supergoop! Sunscreen Is the Secret to Glowy Skin", "Amazon's secret weapon against Walmart could be a massive blitz…" (precision gap, 2026-11-12). */
    private val NAMED_SECRET = Regex(
        """\b(?:is|are|was)\s+the\s+secret\s+to\b|\bsecret\s+weapon\b[^.?!]{0,40}\b(?:could\s+be|is|was)\s+(?:an?|the|its|[A-Z\d])|\bsecret\s+weapon:\s+\S""",
        RegexOption.IGNORE_CASE,
    )

    private fun labelledOpinion(headline: String, articleType: ArticleType? = null): List<MatchResult> = buildList {
        // The feed or link has already told the reader it is a view.
        if (articleType?.isSignedView == true) add(SpanMatchResult(IntRange.EMPTY, ""))
        LEADING_CAPS_BYLINE.find(headline)?.takeIf { looksLikeByline(it.groupValues[1]) }?.let(::add)
        TRAILING_CAPS_BYLINE.find(headline)?.takeIf { looksLikeByline(it.groupValues[1]) }?.let(::add)
        TRAILING_SECTION.find(headline)?.let(::add)
        TRAILING_SIGNOFF.find(headline)?.let(::add)
        OPINION_LABEL.find(headline)?.let(::add)
        TRAILING_NAME_CREDIT.find(headline)?.let(::add)
        FIRST_PERSON_VIEW.find(headline)?.let(::add)
    }

    /** Where this signal first fires in the headline, or null if it doesn't. */
    fun matches(signal: String, headline: String, allCapsAllowlist: Set<String>, articleType: ArticleType? = null): MatchResult? =
        allMatches(signal, headline, SignalContext(allCapsAllowlist, articleType)).firstOrNull()

    /** Where this signal first fires in the headline, or null if it doesn't. */
    fun matches(signal: String, headline: String, context: SignalContext): MatchResult? =
        allMatches(signal, headline, context).firstOrNull()

    /**
     * What a signal may know besides the headline's words: acronyms the rule
     * file allows in capitals, and what the article says it is (its type from
     * the feed or link). Never who published it.
     */
    data class SignalContext(
        val allCapsAllowlist: Set<String>,
        val articleType: ArticleType? = null,
        /** The feed summary and the story's opening. They only settle facts that remove a hit (codebook §1). */
        val summary: String? = null,
        val storyLead: String? = null,
    )

    /** Fun interface for matching a signal against a headline. */
    internal fun interface SignalMatcher {
        fun match(headline: String, context: SignalContext): List<MatchResult>
    }

    /**
     * A signal that is one regular expression, so the linter can check it
     * alternative by alternative. [keep], when given, drops matches the
     * pattern alone cannot rule out, such as a tease whose answer follows.
     */
    internal class RegexSignal(
        val regex: Regex,
        private val keep: ((headline: String, match: MatchResult) -> Boolean)? = null,
        private val applies: ((SignalContext) -> Boolean)? = null,
    ) : SignalMatcher {
        override fun match(headline: String, context: SignalContext): List<MatchResult> =
            if (applies?.invoke(context) == false) emptyList()
            else regex.findAll(headline).filter { keep?.invoke(headline, it) ?: true }.toList()
    }

    private fun regexMatcher(
        regex: Regex,
        keep: ((headline: String, match: MatchResult) -> Boolean)? = null,
    ): SignalMatcher = RegexSignal(regex, keep)

    /** A regex signal that stands down when the article context settles the fact it relies on. */
    private fun contextGatedMatcher(regex: Regex, applies: (SignalContext) -> Boolean): SignalMatcher =
        RegexSignal(regex, applies = applies)

    /** The regular expression behind a signal, or null if the signal is written as code. */
    fun regexOf(signal: String): Regex? = (REGISTRY[signal] as? RegexSignal)?.regex

    private fun genrePrefixSubtitle(headline: String): List<MatchResult> {
        val match = GENRE_PREFIX_SEPARATOR.find(headline) ?: return emptyList()
        val start = match.range.last + 1
        return if (start < headline.length) {
            listOf(SpanMatchResult(start until headline.length, headline.substring(start)))
        } else {
            emptyList()
        }
    }

    private fun filterGenreSubtitle(matches: List<MatchResult>, headline: String): List<MatchResult> {
        if (matches.isEmpty()) return matches
        val subtitle = genrePrefixSubtitle(headline).firstOrNull()?.range ?: return matches
        return matches.filterNot { subtitle.overlaps(it.range) }
    }

    private fun cataphoricDeicticLead(headline: String): List<MatchResult> =
        filterGenreSubtitle(CATAPHORIC_DEICTIC_LEAD.findAll(headline).toList(), headline)

    private fun trailingRhetoricalQuestion(headline: String): List<MatchResult> =
        filterGenreSubtitle(TRAILING_RHETORICAL_QUESTION.findAll(headline).toList(), headline)

    private fun rhetoricalQuestionHook(headline: String): List<MatchResult> =
        filterGenreSubtitle(
            RHETORICAL_QUESTION_HOOK.findAll(headline).toList() + RHETORICAL_QUESTION_FULL.findAll(headline).toList(),
            headline,
        )

    private fun leadingExclamationHook(headline: String): List<MatchResult> =
        filterGenreSubtitle(LEADING_EXCLAMATION_HOOK.findAll(headline).toList(), headline)

    private fun imperativeReactionHook(headline: String): List<MatchResult> =
        filterGenreSubtitle(IMPERATIVE_REACTION_HOOK.findAll(headline).toList(), headline)

    private fun prescriptiveBehavioralImperative(headline: String): List<MatchResult> =
        filterGenreSubtitle(PRESCRIPTIVE_BEHAVIORAL_IMPERATIVE.findAll(headline).toList(), headline)

    /**
     * A headline that opens on unnamed experts as the authority: "Experts Just
     * Updated…", "Economists warn…", "Scientists say…". Codebook v6: authority
     * borrowed from people nobody can check. Rule pack 28, mined from the
     * trigger words marked in mined-2026-10 (20 of its misses).
     *
     * Codebook v11 (rule pack 32): scientists or researchers reporting a
     * finding ("Scientists Discover a Surprising Link…") are judged as
     * science, not sourcing, so for them only a view or a warning counts.
     */
    private val UNNAMED_EXPERT_LEAD = Regex(
        """\b(?:after|as|when)\s+experts\s+(?:make|find|found|discover|reveal|uncover|spot)\w*|""" +
            """(?:^\W*|\?\s+)(?:Experts?|Economists?|Analysts?|Doctors?|Engineers?)\s+""" +
            """(?:(?:just|have|may|accidentally|finally|now)\s+)?""" +
            """(?:discover|detect|find|found|reveal|uncover|identif|creat|revers|extend|catch|caught|say|warn|believe|question|issue|updat|weigh|unleash|spot|solve|crack|rule)\w*|""" +
            """(?:^\W*|\?\s+)(?:Scientists?|Researchers?|Astronomers?|Physicists?|Biologists?)\s+""" +
            """(?:(?:just|have|may|now)\s+)?(?:say|warn|believe|question|issue|weigh)\w*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A label put on something with nobody named as putting it there: "new
     * rules are branded a 'nightmare'", "…was dubbed 'the worst ever'". The
     * passive hides who said it, so it is anonymous sourcing, not conflict
     * (rule pack 33, from the 2026-10 flag review; the Daily Mail story names
     * nobody either). "…branded a liar by Starmer" names the speaker.
     */
    private val AGENTLESS_LABEL = Regex(
        """\b(?:is|are|was|were|been|being|be|gets?|got)\s+(?:being\s+)?(?:branded|dubbed|labell?ed|derided\s+as|slammed\s+as|decried\s+as)\s+(?![^.,;:!?|]*\bby\b)""" +
            """(?:(?:an?|the)\s+)?(?:['‘"“][^'’"”]{1,40}['’"”]|[\p{L}-]+(?:\s+[\p{L}-]+)?)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Unnamed scientists announcing "a way to" beat a disease or a harm, with
     * no stage given: "Scientists find a way to fight Alzheimer's brain damage
     * without removing toxic tau". Codebook v11: a cure-style claim credited to
     * nobody the reader can check is early science. A stated stage ("…in
     * mice") is honest and cancels it (principle 7). Rule pack 32, from the
     * 2026-10 flag review, where four such headlines read as
     * science hype; it fires on none of the 15,681 tuning rows labeled NONE.
     */
    private val UNNAMED_WAY_TO_CURE = Regex(
        """(?:^\W*|\?\s+)(?:Scientists?|Researchers?)\s+(?:(?:just|have|may|finally|now)\s+)?""" +
            """(?:find|found|discover\w*|develop\w*|uncover\w*|create\w*|identif\w*)\s+(?:an?\s+)?(?:new\s+|simple\s+|surprising\s+|potential\s+)?""" +
            """ways?\s+to\s+(?:fight|kill|beat|cure|stop|reverse|treat|prevent|block|slow|halt|destroy|eliminate|defeat|protect|repair|restore|regrow)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val STATED_STAGE = Regex("""\bin\s+(?:mice|rats|cells|worms|flies|the\s+lab|zebrafish|animals)\b""", RegexOption.IGNORE_CASE)

    private fun unnamedWayToCure(headline: String): List<MatchResult> =
        if (STATED_STAGE.containsMatchIn(headline)) emptyList() else UNNAMED_WAY_TO_CURE.findAll(headline).toList()

    /**
     * The same unnamed group credited at the end: "…, researchers say",
     * "…, advocates say", "…, fire officials say". Codebook v6. A named
     * institution in front ("Pentagon officials say") is muted by the
     * category's named-attribution check. Since codebook v11 "…, researchers
     * find" is a finding, judged as science, not sourcing.
     */
    private val UNNAMED_GROUP_CREDIT = Regex(
        """,?\s+(?:(?:experts?|analysts?|economists?|officials?|advocates|insiders)\s+(?:say|said|warn|believe|find|found|predict|fear)|(?:scientists?|researchers?)\s+(?:say|said|warn|believe|predict|fear))\s*[.!]?\s*$|,\s*(?:an?\s+)?(?:expert|insider)\s+(?:says|warns|claims)\s*[.!]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "Report:", ": Report" and "reportedly" when the headline names no outlet
     * (codebook v4, Q3). A credit to a named outlet ("…, Bloomberg reports",
     * "according to the FT") is someone the reader can check.
     */
    private val UNATTRIBUTED_REPORT = Regex("""^\W*Reports?\s*:|:\s*Reports?\s*$|\breportedly\b|\bamid\s+(?:[\p{L}-]+\s+){0,2}rumou?rs\b|\breports\s+(?:reveal|say|suggest|claim)\b|,\s*reports\s+say\s*$|\brumou?red\s+to\b""", RegexOption.IGNORE_CASE)
    /**
     * A court or a legislature acting in public: "Judge orders Trump to sit for
     * deposition…: Reports". The act is on the record, so a "Reports" credit
     * borrows nothing (codebook v12, 2026-10-23).
     */
    private val PUBLIC_ACT = Regex(
        """\b(?:judges?|court|jury|juries|justices|tribunal|magistrates?|senate|congress|parliament|house)\s+(?:[\p{L}-]+\s+)?""" +
            """(?:orders?|ordered|rules?|ruled|blocks?|blocked|halts?|halted|dismiss(?:es|ed)?|denies|denied|grants?|granted|""" +
            """sentences?|sentenced|approves?|approved|rejects?|rejected|passes|passed|votes?|voted|strikes?\s+down|struck\s+down|upholds?|upheld)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val NAMED_OUTLET_CREDIT = Regex("""\b(?:according\s+to|per|via)\s+(?:the\s+)?[A-Z]|\b[A-Z][\p{L}.&]*(?:\s+[A-Z][\p{L}.&]*)*\s+(?i:reports|reported)\b""")

    /**
     * An unnamed study, survey, poll or report as the authority (codebook v7,
     * 2026-10-19): "Study Finds…", "…, study reveals", "Poll:",
     * "…: Survey". An unnamed report is not counted (codebook v8). A named body ("Pew poll", "ONS figures")
     * is not matched: the pattern needs the bare noun at the start or end.
     */
    /**
     * The summary or the story's opening names who did the study: "A study by
     * DZNE…", "researchers at the Carnegie Institution", "published in Nature
     * Communications". Codebook v12 (2026-10-23): then "…, study shows"
     * is ordinary shorthand, not borrowed authority.
     */
    private val STUDY_SOURCE = Regex(
        """\b(?:stud(?:y|ies)|survey|poll|research|team|researchers|scientists)\s+(?:by|from|at|led\s+by|of)\s+(?:the\s+)?\p{Lu}|""" +
            """\b(?:published|appears?)\s+in\s+(?:the\s+)?(?:journal\s+)?\p{Lu}|""" +
            """\b(?:University|Institute|Institution|College|Laborator(?:y|ies)|Hospital|Academy|School\s+of|Cent(?:er|re)\s+for)\b|""" +
            """\b(?:Pew|Gallup|YouGov|Ipsos|ONS|NASA|NIH|CDC|NOAA)\b""",
    )

    private fun namesStudySource(context: SignalContext): Boolean =
        listOfNotNull(context.summary, context.storyLead).any { STUDY_SOURCE.containsMatchIn(it) }

    private val UNNAMED_STUDY = Regex(
        """^\W*(?:New\s+|A\s+)?Study\s+(?:finds|found|reveals|shows|suggests|says)\b|^\W*(?:Poll|Survey)\s*:|:\s*(?:Poll|Study|Survey)\s*$|""" +
            """,\s*(?:a\s+)?(?:new\s+)?(?:study|survey|poll)\s+(?:finds|says|shows|reveals|suggests)\s*$|,\s*says\s+(?:a\s+)?new\s+(?:study|survey)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A closing "…revealed" or "…confirmed" over the very thing the headline
     * withholds: "RJ Mitte's Cause of Death Revealed", "your results revealed",
     * "Last Days … Revealed". An announcement ("Fourth RuneScape officially
     * revealed") names its subject, so the withheld noun must come first.
     */
    private val WITHHELD_REVEAL_ENDING = Regex(
        """\b(?:cause\s+of\s+death|real\s+age|age|first\s+words|results|exact\s+date|identity|fate|reason|real\s+name|next\s+(?:career\s+)?move|last\s+days|secret|salary|replacement|winner)\b[^.:]{0,25}?(?:revealed|confirmed|explained)\s*[.!]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The other closings that announce an answer and stop (codebook rule 5):
     * "Here's everything we know.", "…here's what you should know", "Now We
     * Know Why.", "Scientists Say They've Found The Sweet Spot."
     */
    private val ANNOUNCED_ANSWER_CLOSING = Regex(
        """(?:\bhere(?:'s|s|\s+is)\s+(?:everything|all)\s+(?:we|you)\s+(?:know|need\s+to\s+know|should\s+know)|(?:(?<=[.!]\s)|(?<=\s[-–—]\s)|(?<=:\s))here(?:'s|s|\s+is)\s+what\s+you\s+(?:should|ought\s+to)\s+know|\bnow\s+we\s+(?:finally\s+)?know\s+(?:why|how)\b|\b(?:say|think)\s+they(?:'ve|\s+have)\s+found\s+the\s+\w+)\s*[.!]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A counted payoff the headline holds back (codebook v4, Q6): "Here Are 5
     * Things He Learned", "5 details you might have missed", "three-word
     * warning", "urged not to do two things". A how-to that names its subject
     * ("Ask Your Spouse These 3 Questions First") stays service: it has no
     * noun from this list.
     */
    private val NUMBERED_PAYOFF = Regex(
        """\b(?:these|here\s+are|the)\s+(?:\d+|two|three|four|five|six|seven|eight|nine|ten)\s+(?:[\w-]+\s+)?(?:things|details|reasons|signs|words|secrets|tips|hacks|habits|attributes|figures|games|species|items|stocks)\b|""" +
            """\b(?:\d+|two|three|four|five)\s+(?:[\w-]+\s+)?(?:things|details|words|attributes)\s+(?:you\s+(?:might\s+have\s+|may\s+have\s+)?missed|police|he|she|they|I|we)\b|""" +
            """\b(?:two|three|four|five|\d)-word\s+(?:warning|message|reply|response|answer|plea|text)\b|""" +
            """\b(?:not\s+)?to\s+(?:do|write|say|avoid)\s+(?:two|three|four|\d)\s+(?:things|words)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A subject described to make you want its name (codebook v4, Q1): "a
     * Surprising Genetic Explanation", "One Everyday Habit", "Only one
     * workout", "a hidden trigger". "Hidden" counts only before a word for a
     * cause or consequence: "a hidden chamber" is a place. An answer after a
     * colon ("…a surprising threat: Too many trees") gives the name.
     */
    private val INTRIGUE_SUBJECT = Regex(
        """(?:\b(?:a|an|one|this)\s+(?:surprising(?:ly)?|strange|weird|bizarre|mysterious|unusual|unexpected|little-known)\s+(?:new\s+)?\p{L}+|""" +
            """\b(?:a|an|the|this)\s+hidden\s+(?:link|cause|trigger|driver|mechanism|downside|danger|risk|factor|reason|benefit|explanation|message|signal|clue|cost|network|threat)\b|""" +
            """^\W*One\s+(?:simple|everyday|common|easy|small|surprising)\s+(?:habit|trick|thing|food|change|exercise|drink|mistake|phrase|workout|ingredient)\b|""" +
            """^\W*Only\s+one\s+\w+|""" +
            // "A sleeper Senate battleground…", "one of the biggest fears about…", "one of the scariest games I have ever played".
            """\b(?:a|an)\s+(?:sleeper|dark-horse)\s+\p{L}+|\bone\s+of\s+the\s+(?:biggest|worst|scariest|strangest|weirdest|most\s+\p{L}+)\s+(?!in\b|of\b)\p{L}+|\bone\s+of\s+(?:this|the|last)\s+(?:year|week|season|decade)['’]s\s+(?:biggest|worst|most\s+\p{L}+)\b)(?![^:]*:\s*[A-Z]\p{L}+\s+\p{L}+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * An outcome dangled and withheld: "did something nobody ever expected",
     * "Webb Found Something Else", "The Craters Revealed a Surprise", "but not
     * the way you think", "It's Even Weirder Than It Sounds", "There are others".
     */
    private val WITHHELD_OUTCOME_HOOK = Regex(
        """\b(?:doing|did|found|spotted|noticed)\s+something\s+(?:unusual|strange|unexpected|incredible|odd)|\bsomething\s+(?:nobody|no\s+one)\s+(?:ever\s+)?expected|\bbut\s+not\s+(?:in\s+)?the\s+way\s+you\s+(?:think|expect)|\beven\s+weirder\s+than\s+it\s+sounds|\bthere\s+are\s+others\s*[.!]?\s*$|\bfound\s+something\s+else\b|\brevealed\s+a\s+surprise\b|^\W*See\s+(?:who|what|how|why|which|where)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A label standing where the news should be: "Major update after…",
     * "Police update as…", and a fond description in place of a name opening
     * the headline ("Beloved soap star, 59, dies…", "Beloved UK crisp brand…").
     */
    private val WITHHELD_SUBJECT_LABEL = Regex(
        """^\W*(?:Major\s+)?(?:new\s+)?update\s+(?:after|as|on|in)\b|^\W*Police\s+(?:issue\s+)?update\s+(?:after|as|on)\b|""" +
            """^\W*(?:Beloved|Iconic|Legendary|Much-loved|Well-known|Notorious)\s+(?:UK\s+|British\s+|US\s+|TV\s+|Australian\s+)?(?:soap\s+star|star|actor|actress|singer|brand|chain|restaurant|shop|store|pub|band|presenter|show|crisp\s+brand|retailer)\b|""" +
            // A role in place of the name, straight before the news: "Hollywood actress dies at 48…".
            """^\W*(?:[A-Z][\p{L}-]+\s+){0,2}(?:actress|actor|star|singer|presenter|legend|icon|footballer|comedian|rapper|model|influencer|TV\s+chef)(?:,\s*\d+,)?\s+(?:dies|dead|died|rushed|hospitali[sz]ed|diagnosed|quits|splits|announces|confirms|reveals|admits|shares|breaks|pays|issues|wows|stuns|shocks|unveils)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A bare pronoun standing in for the person the story is about (codebook
     * v6): "He Wants AI Benchmarking To Be a Science…", "She Wanted to Have
     * Three Kids. Then Life Happened." A pronoun inside an opening quotation
     * ("'He persuaded us': …") is the speaker's word, so the headline must
     * start on the pronoun itself.
     */
    private val PRONOUN_SUBJECT_LEAD = Regex("""^(?:He|She|They)\s+(?!(?i:said|says)\b)\p{L}+\b""")

    /**
     * A verdict or reason announced and held back: "Rooney gives verdict on
     * Gravenberch red card", "Trump gives reason for waiving sanctions", "Trump
     * tells Axios why…". An answer after a colon gives it.
     */
    private val WITHHELD_VERDICT = Regex(
        """\b(?:gives|delivers|offers|issues)\s+(?:(?:his|her|their|a|brutal|honest|blunt|damning|stark|surprising)\s+){0,2}(?:verdict|reason|explanation|answer)\s+(?:on|for|over|after|as)\b|\btells\s+(?:[A-Z]\p{L}+\s+){1,2}why\b|^\W*What\s+.{2,40}?\s+reveals\s+about\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * One person characterizing a rival's remark (codebook v6,
     * 2026-10-19): "Susan Collins Says Rival's 'Oh, Shut Up' Comment Reflects
     * How He Treats Women". Somebody's words staged as a fight. The match is
     * only "Rival's": the speaker's name and the quote are muted spans, so
     * the rest is checked by lookarounds (fixed-length, for Android's ICU).
     */
    private val CHARACTERISED_REMARK = Regex(
        """(?:(?<=\bsays\s)|(?<=\bclaims\s))(?:(?:his|her|their)\s+)?(?:rival|opponent|critic|predecessor|ex|enemy|successor)['’]s(?=\s+.{0,40}?\b(?:comments?|remarks?|jibe|attack|words|outburst)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Codebook v7 leading question (`opinion_as_fact`): a question that implies
     * its answer or plants doubt. "Is X really…?", "But at what cost?", "Is
     * this the end of X?", "How long until…?", "Cars, who needs them?". A
     * plain question the piece answers ("What is El Niño?") has none of these
     * markers.
     */
    private val LEADING_QUESTION = Regex(
        """\bbut\s+at\s+what\s+cost\?|\b(?:spells?|signals?)\s+(?:trouble|doom|disaster|the\s+end)\b[^.!]*\?|""" +
            """^\W*(?:[^:?]{0,40}:\s*)?(?:Is|Are|Has|Have|Does|Do|Did|Can|Could|Will|Was)\s+[^?]{3,80}?\b(?:really|finally|actually|ever|already|slowly|secretly)\b[^?]*\?|""" +
            """\bHas\s+the\s+(?:next|end|age|era)\b[^?]*\b(?:begun|arrived|come|started)\?|""" +
            """(?<=[.!]\s)What\s+(?:does|do|did)\s+[^?]{3,40}?\b(?:really|actually)\b[^?]*\?|""" +
            """\bIs\s+(?:this|it|that)\s+the\s+(?:end|death|beginning\s+of\s+the\s+end)\s+of\b|""" +
            """\bHow\s+long\s+(?:until|before)\b[^?]*\?|\b\p{L}+,\s+who\s+needs\s+(?:them|it)\?|^\W*(?:Beyond|Goodbye)\s+[\p{L} ]{3,30}\?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Codebook v7 sweeping verdict (`opinion_as_fact`): an absolute verdict in
     * the outlet's voice. "has changed everything", "will never be the same",
     * "turning … upside down", "Everything you've been told … is a lie".
     */
    private val SWEEPING_VERDICT = Regex(
        """\b(?:has|have|had)\s+changed\s+(?:everything|forever|the\s+game)\b|\bchanged\s+[\p{L}-]+\s+forever\b|""" +
            """\bwill\s+never\s+be\s+the\s+same\b|\bturn(?:s|ed|ing)?\s+(?:the\s+)?(?:[\p{L}-]+\s+){0,3}upside\s+down\b|""" +
            """\beverything\s+you(?:['’]ve|\s+have)\s+been\s+told\b|""" +
            """\bis\s+(?:simply|undeniably|unquestionably|officially)\s+(?:better|back|the\s+best|over|dead|broken)\b|\bundeniably\b|""" +
            """\bwas\s+never\s+really\s+about\b|\bthe\s+most\s+[\p{L}-]+\s+[\p{L}-]+\s+in\s+(?:years|decades|a\s+generation)\b|""" +
            """\bjust\s+fell\s+apart\b|\bgambling\s+everything\b|\bis\s+a\s+lie\b|""" +
            // The outlets check, 2026-11-13: a promise about the reader's own reaction ("33+ Advent calendars so good you'll open all the doors on the first day").
            """\bso\s+[\p{L}-]+\s+(?:that\s+)?you(?:['’]ll|\s+will|\s+won['’]t|\s+can['’]t)\b|""" +
            // A closing verdict sentence in the outlet's voice: "The Hegseth Vote Was Jeffries's First Big Test. He Failed."
            """(?<=[.!?]\s)(?:He|She|They|It|We)\s+(?:Failed|Blew\s+It|Lost|Lied|Flunked|Flopped)\.?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Codebook v7 insinuation (`opinion_as_fact`): words that hint at
     * wrongdoing without stating it. "conveniently", "quietly scraps",
     * "raises questions about". "quietly" as plain manner ("quietly
     * released a patch") is not here.
     */
    private val INSINUATION = Regex(
        """\bconveniently\b|""" +
            """\bquietly\s+(?:drops?|dropped|cuts?|scraps?|scrapped|removes?|removed|deletes?|deleted|rolls?\s+back|reverses?|ditches?|ditched|axes?|axed|buries|buried|walks?\s+back|abandons?|abandoned|guts?|gutted)\b|""" +
            """\braises?\s+(?:fresh\s+|new\s+|serious\s+)?questions\s+(?:about|over)\b|""" +
            """\bdespite\s+(?:his|her|their)\s+denials\b|""" +
            """\bfuel(?:s|ing)?\s+(?:new\s+|fresh\s+)?(?:allegations|speculation|suspicion|rumou?rs)\b|^\W*Exposed\s*:""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Codebook v7 loaded label (`opinion_as_fact`): a sneer word put on a
     * person or group in the outlet's voice. "grifters", "illegals", "tech
     * oligarchs", "AI slop", "radical left".
     */
    private val LOADED_LABEL = Regex(
        """\bgrift(?:ing|er|ers)\b|\billegals\b|\b(?-i:Big\s+[A-Z]\p{L}+)\s+cartel\b|\boverrated\b|\bsex\s+pest\b|\bpaedo\b|\bscumbag\b|""" +
            """\b(?:tech\s+)?oligarchs\b|\bclunker\b|\bhorrendous\b|\banti-science\b|\bslop\b|\boverlords\b|\buseless\s+\p{L}+|""" +
            """\bradical\s+left(?:ist)?\b|\bscapegoats?\b|\bfloundering\b|\bagenda\s+obsession\b|\bpropaganda\s+(?:trip|stunt|machine)\b|""" +
            """\bthugs?\b|\bcareer\s+criminals?\b|\bSoros-(?:linked|backed|funded)\b|\bgaslighting\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Codebook v7 sarcasm and mockery (`manufactured_rage`): snark at someone
     * in the outlet's voice. "So much for…", "All hail", "not-so-smart",
     * "left red-faced", a punchline sentence ("Just Not Very Well").
     */
    private val MOCKERY = Regex(
        """^\W*So\s+much\s+for\b|\bAll\s+hail\b|\bnot-so-\p{L}+|\bgrovell?ing\b|\beat\s+humble\s+pie\b|""" +
            """\bfrom\s+atop\s+a\s+(?:mountain|pile)\s+of\b|\bleft\s+red[-\s]faced\b|""" +
            """\bnow\s+(?:they|he|she)\s+needs?\s+to\s+find\s+a\b|[.!]\s+Just\s+Not\s+Very\s+Well\b|\(It['’]s\s+short\.|""" +
            """\bWhat\s+could\s+(?:possibly\s+)?go\s+wrong\b|\bwonderful\s+world\s+of\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "Horror" opening a news headline as an adjective or label: "Horror
     * Victorian disease outbreak forces closure of hospital ward". The genre
     * ("Horror film", "Horror fans") is left alone.
     */
    private val HORROR_LEAD = Regex(
        """^\W*Horrors?\s+(?!film|movie|story|stories|novel|game|show|series|genre|fans?|icon|legend|writer|director|author|actor|actress|star|classic|comedy|remake|franchise|sequel|anthology|podcast|festival|season)\p{L}+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Ordinary words set in capitals for emphasis (codebook §4
     * `urgency_pressure`): "after FOUR mega warehouses", "REFUSE to display",
     * "almost DOUBLE", "Katseye UNFOLLOW star". A closed list of everyday
     * English words, so acronyms (USMNT, AUKUS, NBCLA) never match, and the
     * word must sit between lower-case words, so all-caps headlines and caps
     * bylines are left to the other caps signals.
     */
    private val EMPHASIS_CAPS = Regex(
        """(?<=[\p{Ll},]\s)(?:ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN|TWELVE|TWENTY|HUNDREDS|THOUSANDS|MILLIONS|FIRST|SECOND|THIRD|FOURTH|FIFTH|TWICE|DOUBLE|TRIPLE|HALF|""" +
            """ALL|EVERY|EVERYONE|EVERYTHING|ANOTHER|MORE|MUCH|ONLY|JUST|NEVER|ALWAYS|AGAIN|EVER|STILL|EVEN|NOTHING|NOBODY|HUGE|VAST|TINY|MASSIVE|PERFECT|GREAT|BEST|WORST|REAL|TRUE|""" +
            """BANNED|FIRED|QUITS?|DUMPED|BLOCKED|UNFOLLOWS?|UNFOLLOWED|REFUSES?|REFUSED|MURDER|MURDERED|KILLED|KICKED|PUNCHED|SLAPPED|SPITTING|STABBED|ATTACKED|""" +
            """INSIDE|OUTSIDE|OUT|OFF|BACK|NOT|NOW|STOP|PLAY|WEEK|WEEKS|DAYS|HOURS|MINUTES|YEARS|NAKED|NUDE|TOPLESS|BIKINI)(?=\s+[\p{Ll}\d£$])""",
    )

    /**
     * A statement, message or verdict announced and held back: "Lewis Hamilton
     * drops statement after…", "inmate shares message for…", "Strictly judge
     * reveals final texts with…", "Bre Tiesi Gives Rare Update on…". An answer
     * after a colon gives it, and an authority updating the public (police,
     * officials, a council) is plain news.
     */
    private val ANNOUNCED_STATEMENT = Regex(
        """\b(?:reveals?|shares?|details|drops|issues?|gives|lifts\s+lid\s+on|opens\s+up\s+(?:about|on))\s+(?:(?:his|her|their|a|an|the|rare|emotional|heartbreaking|hilarious|cryptic|final|first|new|brutal|honest|candid|heartfelt|surprising)\s+){0,3}""" +
            """(?:statement|response|message|update|texts?|verdict|reaction|moment|admission|confession|thoughts|chat)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val STANCE_GIVEN_AFTER = Regex("""^\s+(?:against|in\s+(?:favou?r|support|defen[cs]e)\s+of|backing|supporting|condemning|defending)\b""", RegexOption.IGNORE_CASE)
    private val AUTHORITY_UPDATE = Regex("""\b(?:police|officers|detectives|officials|council|government|ministry|department|club|family)\s+(?:issues?|gives|shares?|releases?)\s+(?:a\s+)?(?:new\s+|further\s+|latest\s+)?(?:statement|update)\b""", RegexOption.IGNORE_CASE)

    /**
     * A reader's dilemma told in the first person and closed by a question the
     * column answers (codebook rule 5, version 5): "My husband and I are in our
     * 50s… Do we really need a will?", "I’m in my 50s… Do I need long-term-care
     * insurance?". Wider than `advice_dilemma_cliffhanger`: any yes/no question
     * about "I", "we", "it", "this" or "my" after a first-person sentence.
     */
    private val READER_DILEMMA_QUESTION = Regex(
        """^(?=.*\b(?:I|I['’]m|My|We|Our)\b)[^?]{20,}[.!:]\s*(?:Should|Do|Can|Is|Am|Are|Would)\s+(?:I|we|it|this|that|my)\b[^?]{0,90}\?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A first-person test that withholds its result: "I ranked 5 Burger King
     * cheeseburgers… The winner was a great value.", "One earned a spot in my
     * dinner rotation.", "15 things surprised me about…".
     */
    private val RANKED_TEASE = Regex(
        """[.!?]\s+(?:One|Only\s+one|The\s+winner|My\s+favou?rite|The\s+best\s+one|One\s+of\s+them)\s+(?:earned|was|is|stood|surprised|won|beat|needs|stole)\b|\b\d+\s+things\s+(?:surprised|shocked|stunned|amazed)\s+me\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "Here's…" closing a news headline and selling its substance: "Here’s what
     * to buy", "Here’s the real story", "Here’s what retirement savers need to
     * know" (codebook version 5). "Here’s who can get it" stays service.
     */
    private val ANNOUNCED_ANSWER_HERE = Regex(
        """(?<=[.!:]\s)Here['’]s\s+(?:what\s+to\s+buy|the\s+real\s+story|the\s+truth|what\s+[\p{L}-]+(?:\s+[\p{L}-]+)?\s+(?:need|should)\s+to\s+know(?!\s+(?:about|if|before|when|on|for|in)\b))\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A professional with no name as the authority (codebook §4
     * `vague_sourcing`), mined from r/savedyouaclick: "Doctors sound alarm as…",
     * "Psychologists Reveal the 3-Word Phrase…", "…, According to Cruise
     * Experts", "…, according to a dietitian".
     */
    private val UNNAMED_PROFESSIONAL = Regex(
        """^\W*(?:Experts?|Doctors?|Dermatologists?|Psychologists?|Nutritionists?|Dietitians?|Physicians?|Vets?|Therapists?)\s+(?:(?:just|have|now)\s+)?(?:pinpoint|sound|call|reveal|warn|explain|share|issue|urge|name|say)\w*|""" +
            """,?\s+according\s+to\s+(?:an?\s+)?(?:[\p{L}-]+\s+){0,2}(?:experts?|doctors?|dermatologists?|psychologists?|nutritionists?|dietitians?|scientists?|researchers?|insiders?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Stock tease formulas readers spoil on r/savedyouaclick (codebook §4
     * `curiosity_gap`), each checked against every tuning row labeled NONE:
     * "I'm a spine doctor. Here's 1 thing I'd never do", "The #1 habit to
     * break", "…, but there's a catch", "The answer might surprise you",
     * "for one big reason", "Makes big announcement", "if you hear this",
     * "reveals which…", "Warning to anyone with…".
     */
    private val TEASE_FORMULA = Regex(
        """^\W*I['’]m\s+an?\s+[\p{L} -]{2,40}[.:]\s+(?:Here['’]s|This\s+Is)\s+(?:the\s+)?(?:\d+|No\.\s*\d+|One|#1)?\s*Things?\b|""" +
            """^\W*(?:The\s+)?(?:#1|No\.\s*1|One|Number\s+One)\s+(?!of\b)(?:[\p{L}-]+\s+){0,3}(?:Habit|Ingredient|Thing|Trick|Food|Mistake|Way|Tip|Rule|Reason|Drink|Exercise|Item|Change)s?\b|""" +
            """\bno\s+one(?:['’]s|\s+is)\s+talking\s+about\b|\bno\s+one\s+(?:expected|saw\s+coming)\b|\byou['’]ve\s+(?:probably\s+)?never\s+heard\s+of\b|\bbut\s+there(?:['’]s|\s+is)\s+a\s+catch\b|""" +
            """\bthe\s+answer\s+(?:might|may|will)\s+surprise\s+you\b|\bthe\s+(?:surprising|shocking|unexpected)\s+answer\b|\?\s+Hint\s*:|\bhave\s+an\s+answer\s*\.?$|""" +
            """\bfor\s+one\s+(?:big|simple|surprising|key|major)\s+reason\b|\bhas\s+something\s+to\s+say\s+about\b|""" +
            """\b(?:makes|announces?|unveils?|approves?|lands)\s+(?:a\s+)?(?:big|major|huge|massive|surprising|rare|blunt|heartbreaking|shocking|bold|historic|dramatic|sweeping)\s+(?:new\s+)?(?:announcement|comments?|admission|confession|revelation|change|shift|decision|move|job)\b|""" +
            """\b(?:of|tried|hear|try)\s+this\s*[.!]?\s*(?:\(|$)|\bthis\s+type\s+of\b|""" +
            """\b(?:reveals|names|shows|knows|addresses)\s+(?:who|why|which|the\s+(?:greatest|best|worst))\b|^\W*(?:Urgent\s+)?warning\s+to\s+(?:anyone|everyone|all|millions)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A harm the headline states (codebook v8 rule 3): "Two dead after horror crash",
     * "Epping fire horror as locals urged…", "girl, 14, hit by car". A fear word next to a
     * stated harm is description, so this cancels `manufactured_fear`; a fear word that stands
     * in for the facts, or a vague or future threat, still counts.
     */
    /** A harm still to come is not a stated harm: "could crash", "risk of flooding", "set to be hit by". */
    private val FUTURE_HARM_CUE = Regex("""\b(?:could|may|might|will|would|set\s+to|about\s+to|threatens?\s+to|risks?(?:\s+of)?|threat\s+of|fears?\s+of|warns?\s+of)\s+(?:be\s+)?(?:\S+\s+)?$""", RegexOption.IGNORE_CASE)

    private val STATED_HARM = Regex(
        """\b(?:dead|died|dies|killed|kills|deaths?|injured|injuries|wounded|crash(?:es|ed)?|collision|fire|blaze|inferno|""" +
            """stabbed|stabbing|shot\s+dead|shooting|flood(?:ed|ing|s)?|explosion|exploded|attacks?|attacked|attacking|machetes?|struck\s+by|hit\s+by|mauled|drowned|""" +
            """raped|murdered|murders?|assaulted|beaten|victims?)\b""",
        RegexOption.IGNORE_CASE,
    )

    // ---- Withheld slots (rule pack 35) ---------------------------------------
    //
    // The teases below share one shape: a general noun stands where the fact
    // should be ("one controversial detail", "a surprising health decision",
    // "reveals her go-to meal"). The nouns and adjectives vary without end, so
    // a list of whole phrases never keeps up; the shape does not vary. These
    // rules are built from shared word classes, so a word added to a class is
    // known to every rule that uses it.

    /** Adjectives that sell a secret. Size words (big, major, key, new) are left out: plain news uses them. */
    private const val INTRIGUE_WORDS =
        """(?:surprising(?:ly\s+\p{L}+)?|unexpected|shocking|stunning|astonishing|controversial|special|secret|grim|devastating|heartbreaking|heart-aching|wild|bizarre|strange|morbid|scathing|brutal|troubling|unfortunate|head-turning|foul|eerie|harsh|awkward|uncomfortable|genius|unusual|mysterious|offensive|NSFW|hilarious|cryptic|bold|damning|concerning|galling|alarming|worrying|disturbing|incredible|remarkable|extraordinary|startling|sneaky|little-known|simple)"""

    /** General nouns that name a slot without filling it. */
    private const val SLOT_NOUNS =
        """(?:reasons?|details?|changes?|moves?|decisions?|news|announcements?|messages?|warnings?|requests?|rules?|nicknames?|mistakes?|problems?|discover(?:y|ies)|finds?|findings?|factors?|signs?|updates?|predictions?|proposals?|solutions?|comments?|remarks?|theor(?:y|ies)|connections?|twists?|habits?|tricks?|hacks?|shifts?|bans?|admissions?|confessions?|revelations?|statements?|side\s+effects?|things?|sentences?|questions?|ingredients?|realit(?:y|ies)|advice|gaffes?|slip-ups?|truths?|facts?|issues?|milestones?|aspects?|impacts?|effects?|causes?|motives?|reactions?|verdicts?|secrets?|purchases?|gestures?|demands?)"""

    /** What a slot noun says once the headline goes on to fill it: "claim that…", "verdict - 'I didn't like it and it doesn't help'". */
    private val SLOT_FILLED_AFTER = Regex("""^\s+that\b|^\s*[-:]\s*['"](?:\S+\s+){4,}""", RegexOption.IGNORE_CASE)

    private fun slotKept(headline: String, match: MatchResult): Boolean =
        announcedAnswerKept(headline, match) && !SLOT_FILLED_AFTER.containsMatchIn(headline.substring(match.range.last + 1))

    /** "Has a Morbid Prediction for…", "makes surprising health decision", "Has Special Request", "unexpected reason". */
    private val INTRIGUE_SLOT = Regex(
        """\b$INTRIGUE_WORDS\s+(?:(?:new|personal|health|real-life|final|last|first|career|family|royal|medical|financial|on-air|major|big|huge)\s+){0,2}$SLOT_NOUNS\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * One counted thing, held back: "One Controversial Detail", "1 Obvious
     * Thing", "the #1 issue", "one feature was a dealbreaker", and counted
     * words: "9 Words", "Two Words From…", "3 ingredients". A how-to that
     * counts its own steps ("How to Whiten Sheets With 2 Ingredients") is service.
     */
    private val COUNTED_SLOT = Regex(
        """(?<![\p{L}\p{N}-])(?<!\bno\s)(?:one|1|#1|No\.\s?1|number\s+one|a\s+single)\s+['"]?(?:[\p{L}'-]+\s+){0,2}['"]?\s*(?:$SLOT_NOUNS|words?|characters?|types?|ways?|items?|foods?|drinks?|demographics?|generations?|pieces?\s+of\s+(?:\p{L}+\s+)?advice|lines?|phrases?|steps?|exceptions?|catch|features?|conditions?)\b|""" +
            """(?<![\p{L}\p{N}.$£€-])(?:two|three|four|five|six|seven|eight|nine|ten|\d{1,2})[- ](?:simple\s+|little\s+|short\s+|magic\s+)?(?:words?|sentences?|ingredients?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val HOW_TO_LEAD = Regex("""^\W*How\s+to\b""", RegexOption.IGNORE_CASE)

    /** A guess ruled out in place of the answer: "but Not the One You Think", "and it's not Greenland or Iran", "- not salt". */
    private val NEGATED_GUESS = Regex(
        """\bnot\s+(?:the\s+)?(?:one|what|who|where|why|how|reason|way)\s+you(?:'d|\s+would|\s+might)?\s+(?:think|expect)|""" +
            """\b(?:and|but)\s+(?:it|that|he|she)(?:'s|\s+is)\s+not\s+(?!just\b|close\b|even\b|over\b|alone\b|the\s+only\b|going\b|clear\b|enough\b|about\b|a\s+good\b)(?:(?-i:[A-Z])|what\b|who\b|the\s+one\b)|""" +
            """\s-\s*(?:and\s+)?(?:it's\s+)?not\s+(?!a\b|an\b|the\b|just\b|only\b|yet\b|even\b)[\p{L}'][\p{L}' ]{1,28}$|""" +
            """\bisn't\s+(?:what|who|where|the\s+one)\s+you(?:'d)?\s+(?:think|expect)|""" +
            """\b(?:isn't|wasn't)\s+(?-i:[A-Z])[\p{L}.]+(?:\s+(?-i:[A-Z])[\p{L}.]+)*\s+or\s+(?-i:[A-Z])""",
        RegexOption.IGNORE_CASE,
    )

    /** Somebody's reaction to a thing the headline will not say: "can't get over what pours out", "notices what seatmate is doing". */
    private val REACTION_WHAT = Regex(
        """\b(?:can'?t\s+(?:believe|get\s+over)|(?:shocked|stunned|floored|horrified|speechless|amazed|baffled|surprised|appalled|disgusted|mortified|gobsmacked)\s+(?:by|at|after|when\s+\p{L}+\s+(?:sees?|notices?|learns?|finds?|discovers?|reali[sz]es?))|sees|notices|spots|after\s+(?:learning|seeing|finding\s+out|reali[sz]ing|discovering)|reali[sz]ed|reali[sz]es|discovers|finds\s+out)\s+(?:what|how|who|why|where)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A fond description in place of the name, anywhere in the headline: "Popular Supplement Linked to…", "as popular sandwich chain surges". */
    private val FOND_DESCRIPTOR = Regex(
        """(?<!['"])\b(?:popular|beloved|well-known|iconic|legendary|famous|much-loved|well-loved|favou?rite)\s+(?:[\p{L}\p{N}'-]+\s+){0,3}(?:chains?|brands?|supplements?|drinks?|foods?|snacks?|stars?|actors?|actress(?:es)?|singers?|icons?|retailers?|stores?|shops?|banks?|airlines?|services?|restaurants?|apps?|products?|medications?|drugs?|vitamins?|items?|habits?|practices?|bands?|cereals?|sweets?|cand(?:y|ies)|chocolates?|beers?|sodas?|pubs?|comedians?|rappers?|footballers?)\b(?!\s+(?:like|such\s+as|including)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** A disclosure verb with a slot noun for its object: "Reveals Her Daughter's Reaction to…", "reveals motive for…", "uncover cause of…". */
    private val DISCLOSED_SLOT = Regex(
        """\b(?:reveals?|revealed|shares?|shared|confirms?|confirmed|unveils?|unveiled|discloses?|disclosed|admits?|uncovers?|uncovered|unearths?|unearthed|exposes?|identif(?:y|ies|ied)|pinpoints?)\s+""" +
            """(?:(?:his|her|their|its|the|a|an|one|(?-i:[A-Z])[\p{L}\p{N}.-]*(?:\s+(?-i:[A-Z])[\p{L}\p{N}.-]*){0,2}'s?)\s+){0,2}""" +
            """(?:(?!to\b|that\b|as\b|in\b|on\b|at\b|after\b|for\b|with\b|by\b|he\b|she\b|they\b|it\b|and\b)[\p{L}\p{N}'-]+\s+){0,2}""" +
            """(?:reasons?|motives?|secrets?|causes?|reactions?|side\s+effects?|nicknames?|fate|truth|red\s+line|regrets?|verdicts?|factors?|habits?|tricks?|mistakes?|theor(?:y|ies)|connections?|twists?|gaffes?|go-to\s+\p{L}+|favou?rite\s+\p{L}+|(?:exact|release|closure|launch|return)\s+dates?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "…'s Romance With Costar Revealed": the withheld thing, whatever its noun, closed by the reveal. */
    private val SLOT_REVEALED = Regex("""'s?\s+(?:[\p{L}\p{N}'-]+\s+){1,6}(?:revealed|unveiled|unearthed|exposed)\s*$""", RegexOption.IGNORE_CASE)

    /** "an unexpected Metroidvania twist" names the thing: a capitalized word in a sentence-case headline is a name, not a description. */
    private fun describesNotNames(headline: String, match: MatchResult): Boolean {
        val last = match.value.trimEnd().substringAfterLast(' ')
        return match.range.first == 0 || last.firstOrNull()?.isUpperCase() != true || isTitleCase(headline)
    }

    // ---- Miss-check constructions (rule pack 36) ------------------------------
    // Each is one headline called an obvious miss in the November 2026
    // review (edge cases 2026-11-m*), kept narrow: none matched a tuning row
    // labeled NONE except POINTED_PERSON, which matched one.

    /** A fond description owned by the subject, closing the headline in place of the name: "Microsoft shuts down its beloved maker space" (The Garage; edge case 2026-10-f02b). */
    private val FOND_POSSESSIVE = Regex(
        """\b(?:its|his|her|their)\s+(?:beloved|iconic|popular|much-loved|famous|legendary|flagship|cherished)\s+(?:[\w-]+\s+){0,2}[\w-]+$""",
        RegexOption.IGNORE_CASE,
    )

    /** The article is the pictures the headline points at: "See Inside Cells Like Never Before in These Vivid Microscopic Images" (edge case 2026-10-f14). Nothing is held back, so the pointing word is not a tease. */
    private val GALLERY_POINTER = Regex(
        """\b(?:in|with|through|from)\s+these\s+(?:[\w-]+\s+){0,3}(?:images|photos|photographs|pictures|maps|charts|graphics)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A series label and then the object itself: "Look Closer: A Mysterious Pair of Mittens" (edge case 2026-11-f09). The thing is named; "mysterious" describes it. */
    private val LABELLED_OBJECT = Regex(
        """:\s+An?\s+(?:mysterious|strange|curious|surprising|unusual|unexpected)\s+(?:[\w-]+\s+){0,2}of\s+[\w-]+$""",
        RegexOption.IGNORE_CASE,
    )

    /** The news is given before the statement is mentioned: "Gary O'Neill announces dad's death as Ipswich boss shares heartbreaking statement" (2026-11-12: not a tease). */
    private val NEWS_GIVEN_FIRST = Regex(
        """\b(?:announces?|confirms?)\s+(?:[\w'’-]+\s+){0,4}(?:death|passing|retirement|split|divorce|engagement|pregnancy|diagnosis)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val STATEMENT_SLOT = Regex("""\b(?:statement|message|tribute|post)\s*$""", RegexOption.IGNORE_CASE)

    /** A roundup's label after the names it covers: "WATCH: Kim Kardashian, Tay Keith, Millie Bobby Brown: Here's what's trending" (2026-11-12: lazy, not a trick). */
    private val TRENDING_ROUNDUP = Regex("""\bhere(?:'s|’s|s)\s+what(?:'s|’s|\s+is)\s+trending\b""", RegexOption.IGNORE_CASE)

    /**
     * A verb of destruction used of words, not things: "destroys rival in fiery debate", "torched on social media",
     * "savaged over remarks", or the verb in capitals ("Charles DESTROYS Spencer"). The bare verbs are literal too often
     * to flag alone: a fire destroys a church, an arsonist torches cars, a dog savages a child (roadmap 6a, 2026-11-12).
     */
    private val VERBAL_DEMOLITION = Regex(
        """(?i:\b(?:destroys?|destroyed|torches|torched|savages|savaged)\s+(?:(?:his|her|their|a|an|the)\s+)?(?:[\w'’-]+\s+)?(?:rivals?|critics?|opponents?|hecklers?|haters|trolls|interviewer|host|reporter|journalist)\b)|(?i:\b(?:destroys?|destroyed|torches|torched|savages|savaged)\b[^.?!]{0,60}\b(?:in\s+(?:[\w'’-]+\s+){0,2}(?:debate|interview|rant|speech|takedown|exchange|hearing|post|tweet|review|monologue|tirade|response|clip|video|column|op-ed|podcast|broadcast|segment)|on\s+(?:live\s+(?:TV|television|radio|air)|air|Twitter|social\s+media|TikTok|Instagram|Truth\s+Social)|over\s+(?:[\w'’-]+\s+){0,3}(?:comments?|remarks?|claims?|jibe|joke|criticism|post|tweet))\b)|\b(?:DESTROYS|DESTROYED|TORCHES|TORCHED|SAVAGES|SAVAGED)\b""",
    )

    /** A person pointed at and not named: "Why this GOP governor thinks…", "How one teacher cleverly got around…". */
    private val POINTED_PERSON = Regex(
        """^(?:Why|How|What|Where|When)\s+(?:this|these|one)\s+(?:[\w'’-]+\s+){1,4}(?:thinks?|says?|believes?|wants?|fears?|got|left|quit|cleverly|managed|made|turned|beat|saved|lost|won)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A list announced and held back, as the whole headline: "The issues a 'really frustrated' Trump may actually work with Democrats on". */
    private val ANNOUNCED_LIST = Regex(
        """^The\s+(?:\w+\s+)?(?:issues?|reasons?|things?|questions?|moments?|mistakes?|words?)\s+(?:(?:that|which)\s+)?(?:an?|the|[A-Z'‘])[^,:?]*\b(?:may|might|could|will|would|can|should|thinks?|says?|wants?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A short closing sentence that points back and holds the news: "…buildout. That might be kaput.", "…Reappeared. This Could Be Why". "It might be…" is a verdict, not a tease, and is left out. */
    private val CLOSING_TEASER = Regex(
        """[.!?]\s+(?:That|This|Those|These)\s+(?:(?:might|may|could)\s+(?:not\s+)?(?:be|soon|change)|(?:is|was)\s+(?:about\s+to|no\s+longer))\b[^.?!]{0,40}[.!]?$""",
        RegexOption.IGNORE_CASE,
    )

    /** A condition described by a famous name in place of its own: "same disorder as Celine Dion". "Same drug as Christa Pike" compares two cases and is left alone. */
    private val BORROWED_NAME_CONDITION = Regex(
        """\b(?:[Ss]ame|[Rr]are|[Mm]ystery|[Mm]ysterious)\s+(?:[\w'’-]+\s+){0,2}(?:disorder|condition|disease|illness|syndrome|cancer|diagnosis|symptoms?)\s+(?:as|that\s+(?:killed|struck|affects?))\s+\p{Lu}""",
    )

    /** A quoted verb credited to a crowd, or a crowd reacting to a thing left out: "'rumbled' by fans", "fans all distracted by the same thing". */
    private val CROWD_CREDIT = Regex(
        """['‘][\w-]+['’]\s+by\s+(?:fans|viewers|shoppers|users|experts|insiders|critics|locals|parents|customers)\b|\b(?:fans|viewers)\s+(?:all\s+)?(?:rumble|spot|notice|work\s+out|think\s+they(?:'|’)ve|convinced|distracted|left)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A poll with no pollster: "Yet another poll shows…", "New poll shows…". Stands down when the article names the source, like UNNAMED_STUDY. */
    private val UNNAMED_POLL = Regex(
        """\b(?:(?:yet\s+)?another|a\s+new|new|latest|shock|bombshell)\s+poll\s+(?:shows?|finds?|suggests?|reveals?|says?|puts?|has)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Two quoted claims and nobody saying them: "…has 'target' on back as he heads to prison – 'risk is high'". One trailing quote, a speaking verb or a colon leaves it alone. */
    private val UNSPOKEN_QUOTES = Regex(
        """^(?!.*\b(?:says?|said|tells?|told|claims?|warns?|admits?|according|insists?|reveals?)\b)(?!.*:)[^'‘’"]*['‘][^'’]{2,30}['’][^'‘’"]*\s[–—-]\s['‘][^'’]{3,40}['’]$""",
        RegexOption.IGNORE_CASE,
    )

    // ---- Outlets check (rule pack 37) -----------------------------------
    // From a check of Know News outlets on 2026-11-13.

    /** A thing described by what the writer once said of it, and not named: "A survival game we once called 'Stalker and Tarkov's alien baby' is closing down". */
    private val RECALLED_DESCRIPTOR = Regex(
        """^An?\s+[^,.:?]{3,50}\s+(?:we|I)\s+(?:once\s+)?(?:called|described|dubbed|named)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A law or rule described by its age and not named: "Plastic pollution is putting an old legal doctrine to a new test". A name given next ("an old law, the Jones Act,") is left alone. */
    private val AGED_UNNAMED_RULE = Regex(
        """\ban?\s+(?:old|obscure|little-known|forgotten|arcane|centuries-old|century-old|decades-old)\s+(?:[\w-]+\s+){0,2}(?:doctrine|law|rule|loophole|statute|provision|clause|treaty)\b(?!\s*[,(:]|\s+(?:called|known\s+as|named)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** The writer promises the reason and keeps it: "…is not a good thing for PC gaming, and I'll tell you why". */
    private val PROMISED_EXPLANATION = Regex(
        """\b(?:I|we)(?:['’]ll|\s+will)\s+(?:tell|show)\s+you\s+(?:why|how|what)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A ranking announced and kept: "Using miles to fly for Thanksgiving? These are the most expensive and cheapest routes". */
    private val THESE_ARE_THE_MOST = Regex(
        """\bThese\s+are\s+the\s+(?:\d+\s+|\w+\s+)?(?:most|best|worst|cheapest|top|biggest|least)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** The reveal is stated, so nothing is held back: "Nexon reveals the extraction shooter has topped 17 million sales". */
    private val STATED_REVEAL = Regex(
        """\breveals?\s+(?:that\s+)?the\s+(?:[\w'’-]+\s+){1,4}(?:has|have)\s+(?:topped|sold|passed|reached|hit|surpassed|shipped|made|grown|fallen|dropped|been)\b""",
        RegexOption.IGNORE_CASE,
    )

    // ---- Reader-nominated shapes (rule pack 36) ------------------------------
    // Ten ways a headline points at a fact and keeps it, read off the 468
    // headlines in savedyouaclick-2026 (tuning-only: real readers called each
    // one out) that the rules missed on 2026-11-12. In the terms of the
    // clickbait-spoiling work the codebook cites (Hagen et al., 2022), each
    // leaves a short phrase or passage the reader must click to get. Every
    // pattern matched at most 2 of about 4,500 tuning rows labeled NONE.

    /** "We Asked Dietitians to Name the Healthiest Snack—They All Chose the Same One". */
    private val SAME_ANSWER = Regex(
        """\b(?:all|both|each)\s+(?:said|chose|picked|named|agreed\s+on|gave|recommended)\s+(?:me\s+)?the\s+same\s+(?:thing|one|answer|brand|[\w-]+)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A "what" clause standing where the thing should be: "because of what was in her knickers", "and what they told me changed my perspective". Opening the headline ("What We Saw in One Venezuelan Gold Town") it is a reporter's title. */
    private val WHAT_CLAUSE = Regex(
        """\b(?:of|for|over|by|at|about|with|after|when|before|until)\s+what\s+(?:(?:was|were|is|are)\s+(?:in|inside|found|spotted|hidden|written|left|on|under|behind|lurking|waiting)|(?:he|she|they|it|we|i)\s+(?:found|saw|did|said|told|names?|named|discovered|heard|wrote|sees|finds|does|says))\b|(?<=\S\s)what\s+(?:he|she|they|i|we)\s+(?:found|saw|discovered|told\s+me|learned)\b[^?]*$""",
        RegexOption.IGNORE_CASE,
    )

    /** A reason announced and not given: "Got Rejected For A Valid Reason", "failing for the most predictable reason", "not there for the reason you think". */
    private val UNSTATED_REASON = Regex(
        """\bfor\s+(?:an?|the|one|this|that|some)\s+(?:[\w'’-]+\s+){0,3}reasons?\s*(?:$|[.!]|[-–—]\s)|\bfor\s+['‘"“][^'’"”]{3,40}reasons?['’"”]?\s*$|\bthe\s+reason\s+you\s+think\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A date, name or setting said to be confirmed, with the value left out: "Confirms 'Creature Commandos' Season 2 Release Date", "Setting Unveiled In New Trailer". A colon or dash after it gives the value. */
    private val WITHHELD_ATTRIBUTE = Regex(
        """\b(?:confirms?|confirmed|reveals?|revealed|unveils?|unveiled|announces?|announced|shares?|shared|addresses)\s+(?:[\w'’&-]+\s+){0,6}(?:release\s+date|start\s+date|premiere\s+date|launch\s+date|return\s+date|setting|fate|cancer\s+type|cause\s+of\s+death)\b(?!\s*(?::|[-–—]\s|is\b|as\b))|\breveals?\s+name\b|\b(?:release\s+date|setting|fate|name)\s+(?:update\s+)?(?:officially\s+)?(?:confirmed|unveiled|revealed|announced|shared)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A kind of thing as the subject where its name belongs: "Huge streaming service phasing out…", "Major city may have to evacuate", "Hollywood icon set to voice…". */
    private val GENERIC_SUBJECT = Regex(
        """^(?:(?:A|An|Another)\s+)?(?:Major|Huge|Popular|Iconic|Beloved|Rising|Leading|Famous|Hollywood|Pop|Rock)\s+(?:[\w-]+\s+){0,2}(?:brand|chain|service|city|destination|company|retailer|airline|icon|star|legend|bank|store|restaurant|carmaker|automaker)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A change or announcement with its size and not its content: "Major change coming to Walmart parking lots", "Travis Kelce Announcement Made on Sunday". */
    private val VAGUE_BIG_CHANGE = Regex(
        """\b(?:major|big|huge|massive)\s+(?:new\s+)?(?:change|update|announcement|move|shake-up|decision)s?\s+(?:coming|to|for|at|about|on|made)\b|\bannouncement\s+made\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A count of things said, banned or predicted, and none of them given: "Just Said 9 Words That…", "Has Banned 3 Types Of Super Bowl Commercials". */
    private val COUNTED_AND_KEPT = Regex(
        """\b(?:said|says|banned|bans|reveals?|shares?|predicts)\s+(?:(?:just|the|these|his|her|next)\s+)?(?:\d{1,2}|two|three|four|five|six|seven|eight|nine|ten)\s+(?:[\w-]+\s+){0,2}(?:words|people|types|things|signs|reasons|foods|items|members|names|rules|mistakes|habits)\b(?!\s*:)""",
        RegexOption.IGNORE_CASE,
    )

    /** An aside that sells instead of telling: "But Of Course There's A Catch", "(It Works Like Magic)", "and it's wild", "and we're honestly not surprised". */
    private val HOOK_ASIDE = Regex(
        """[,–—-]\s*(?:and|but)\s+(?:it(?:'s|’s|\s+is)\s+(?:wild|insane|hilarious|genius|perfect|brilliant|a\s+must-\w+)|of\s+course\s+there(?:'s|’s)\s+a\s+catch|we(?:'re|’re)\s+(?:honestly\s+)?not\s+surprised)|\(It\s+Works\s+[^)]{3,30}\)|\bthere(?:'s|’s)\s+a\s+catch\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A story stopped just before its point: "Woman orders Caesar salad at Disney. Then she feels a crunch…", "That's When We Learned…", ", then has realization about grandma". */
    private val THEN_TWIST = Regex(
        """[.!?—–-]\s*(?:Then|That(?:'s|’s)\s+when|Now)\s+(?:she|he|they|we|it|i)\s+(?:feels?|felt|sees?|saw|has|had|heard|learned|realized?|realised?|noticed?|found)\b|,\s*then\s+(?:has|had|makes?|made)\s+(?:a\s+)?(?:realization|discovery)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "This" alone where the thing should be, or "This Is the most…" with the answer kept: "Add 1/4 Cup of This to Your Pan", "This Is America's No. 1 Pizza Chain, According to 44,000 Diners". */
    private val BARE_THIS = Regex(
        """\b(?:add(?:ing)?|us(?:e|ing)|try(?:ing)?|do(?:ing)?|eat(?:ing)?|drink(?:ing)?|with|of)\s+(?:(?:\d+\/\d+|\d+|a|one)\s+(?:cups?|teaspoons?|tablespoons?|spoonfuls?|drops?|pinch|splash)\s+of\s+)?this\s+(?:to|in|into|for|on|before|after|every|when|and|instead|once|daily)\b|^This\s+Is\s+(?:the|America(?:'s|’s)|Britain(?:'s|’s)|the\s+world(?:'s|’s))\s+['‘“]?(?:No\.?\s?1|#1|most|least|best|worst|[a-z]+est)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val REGISTRY: Map<String, SignalMatcher> = mapOf(
        "intrigue_slot" to regexMatcher(INTRIGUE_SLOT) { headline, match ->
            // A statement mentioned after the headline has given the news is not held back (edge case 2026-11-p05).
            slotKept(headline, match) && !(STATEMENT_SLOT.containsMatchIn(match.value) && NEWS_GIVEN_FIRST.containsMatchIn(headline.substring(0, match.range.first)))
        },
        "counted_slot" to regexMatcher(COUNTED_SLOT) { headline, match -> slotKept(headline, match) && !HOW_TO_LEAD.containsMatchIn(headline) },
        "negated_guess" to regexMatcher(NEGATED_GUESS),
        "reaction_what" to regexMatcher(REACTION_WHAT),
        "fond_descriptor" to regexMatcher(FOND_DESCRIPTOR, ::announcedAnswerKept),
        "pointed_person" to regexMatcher(POINTED_PERSON),
        "announced_list" to regexMatcher(ANNOUNCED_LIST),
        "closing_teaser" to regexMatcher(CLOSING_TEASER),
        "recalled_descriptor" to regexMatcher(RECALLED_DESCRIPTOR, ::announcedAnswerKept),
        "promised_explanation" to regexMatcher(PROMISED_EXPLANATION, ::announcedAnswerKept),
        "these_are_the_most" to regexMatcher(THESE_ARE_THE_MOST, ::announcedAnswerKept),
        "stated_reveal" to regexMatcher(STATED_REVEAL),
        "aged_unnamed_rule" to regexMatcher(AGED_UNNAMED_RULE, ::announcedAnswerKept),
        "borrowed_name_condition" to regexMatcher(BORROWED_NAME_CONDITION),
        "crowd_credit" to regexMatcher(CROWD_CREDIT),
        "unnamed_poll" to contextGatedMatcher(UNNAMED_POLL) { !namesStudySource(it) },
        "unspoken_quotes" to regexMatcher(UNSPOKEN_QUOTES),
        "named_secret" to regexMatcher(NAMED_SECRET),
        "fond_possessive" to regexMatcher(FOND_POSSESSIVE),
        "verbal_demolition" to regexMatcher(VERBAL_DEMOLITION),
        "same_answer" to regexMatcher(SAME_ANSWER),
        "what_clause" to regexMatcher(WHAT_CLAUSE),
        "unstated_reason" to regexMatcher(UNSTATED_REASON, ::announcedAnswerKept),
        "withheld_attribute" to regexMatcher(WITHHELD_ATTRIBUTE, ::announcedAnswerKept),
        "generic_subject" to regexMatcher(GENERIC_SUBJECT, ::announcedAnswerKept),
        "vague_big_change" to regexMatcher(VAGUE_BIG_CHANGE),
        "counted_and_kept" to regexMatcher(COUNTED_AND_KEPT),
        "hook_aside" to regexMatcher(HOOK_ASIDE),
        "then_twist" to regexMatcher(THEN_TWIST),
        "bare_this" to regexMatcher(BARE_THIS),
        "disclosed_slot" to regexMatcher(DISCLOSED_SLOT, ::slotKept),
        "slot_revealed" to regexMatcher(SLOT_REVEALED),
        "multiple_exclamations" to regexMatcher(MANY_BANGS),
        "numeric_claim" to regexMatcher(NUMERIC_CLAIM),
        "teaser_prefix" to regexMatcher(TEASER_PREFIX),
        "question_headline" to regexMatcher(QUESTION),
        "scare_quotes" to SignalMatcher { headline, _ -> scareQuotes(headline) },
        "dramatic_scare_quote" to SignalMatcher { headline, _ -> dramaticScareQuote(headline) },
        "trailing_sources_credit" to regexMatcher(TRAILING_SOURCES_CREDIT),
        "quoted_speech" to regexMatcher(QUOTED_SPEECH),
        "labelled_opinion" to SignalMatcher { headline, context -> labelledOpinion(headline, context.articleType) },
        "breaking_label" to regexMatcher(BREAKING_LABEL),
        "just_in_label" to regexMatcher(JUST_IN_LABEL),
        "named_attribution" to SignalMatcher { headline, _ -> namedAttribution(headline) },
        "literal_event_or_alert" to regexMatcher(LITERAL_EVENT_OR_ALERT),
        "mid_headline_caps" to SignalMatcher { headline, context -> midHeadlineCaps(headline, context.allCapsAllowlist) },
        "second_person_threat" to regexMatcher(SECOND_PERSON_THREAT),
        "leading_exclamation_hook" to SignalMatcher { headline, _ -> leadingExclamationHook(headline) },
        "rhetorical_question_hook" to SignalMatcher { headline, _ -> rhetoricalQuestionHook(headline) },
        "teaser_profile_lead" to regexMatcher(TEASER_PROFILE_LEAD),
        "celebrity_reaction_formula" to regexMatcher(CELEBRITY_REACTION_FORMULA),
        "two_sentence_cliffhanger" to regexMatcher(TWO_SENTENCE_CLIFFHANGER),
        "conflict_staging" to regexMatcher(CONFLICT_STAGING),
        "staged_exchange" to regexMatcher(STAGED_EXCHANGE),
        "tabloid_shout_caps" to SignalMatcher { headline, context -> tabloidShoutCaps(headline, context.allCapsAllowlist) },
        "betteridge_superlative_question" to regexMatcher(BETTERIDGE_SUPERLATIVE_QUESTION),
        "viral_emotional_imperative" to regexMatcher(VIRAL_EMOTIONAL_IMPERATIVE),
        "ingroup_identity_hook" to regexMatcher(INGROUP_IDENTITY_HOOK),
        "cataphoric_deictic_lead" to SignalMatcher { headline, _ -> cataphoricDeicticLead(headline) },
        "quiz_self_id_formula" to regexMatcher(QUIZ_SELF_ID_FORMULA),
        "viral_prescriptive_command" to regexMatcher(VIRAL_PRESCRIPTIVE_COMMAND),
        "viral_listicle_hook" to regexMatcher(VIRAL_LISTICLE_HOOK),
        "we_know_based_on" to regexMatcher(WE_KNOW_BASED_ON),
        "challenge_quiz_prompt" to regexMatcher(CHALLENGE_QUIZ_PROMPT),
        "how_well_do_you" to regexMatcher(HOW_WELL_DO_YOU),
        "what_would_your_be" to regexMatcher(WHAT_WOULD_YOUR_BE),
        "superlative_listicle_lead" to regexMatcher(SUPERLATIVE_LISTICLE_LEAD),
        "cliffhanger_consequence" to regexMatcher(CLIFFHANGER_CONSEQUENCE),
        "second_person_viral_hacks" to regexMatcher(SECOND_PERSON_VIRAL_HACKS),
        "deictic_viral_subject" to regexMatcher(DEICTIC_VIRAL_SUBJECT),
        "viral_first_person_lead" to regexMatcher(VIRAL_FIRST_PERSON_LEAD),
        "perfect_response_formula" to regexMatcher(PERFECT_RESPONSE_FORMULA),
        "viral_meme_frame" to regexMatcher(VIRAL_MEME_FRAME),
        "sports_fury" to regexMatcher(SPORTS_FURY),
        "accusatory_habit_listicle" to regexMatcher(ACCUSATORY_HABIT_LISTICLE),
        "withheld_discovery_hook" to regexMatcher(WITHHELD_DISCOVERY_HOOK),
        "tell_all_why_hook" to regexMatcher(TELL_ALL_WHY_HOOK, ::announcedAnswerKept),
        "withheld_statement" to SignalMatcher { headline, _ -> withheldStatement(headline) },
        "speculative_financial_cataphora" to regexMatcher(SPECULATIVE_FINANCIAL_CATAPHORA),
        "trailing_rhetorical_question" to SignalMatcher { headline, _ -> trailingRhetoricalQuestion(headline) },
        "broadsheet_feature_why" to regexMatcher(BROADSHEET_FEATURE_WHY),
        "imperative_reaction_hook" to SignalMatcher { headline, _ -> imperativeReactionHook(headline) },
        "cataphoric_threshold" to regexMatcher(CATAPHORIC_THRESHOLD),
        "withheld_answer_here" to regexMatcher(WITHHELD_ANSWER_HERE) { headline, match -> announcedAnswerKept(headline, match) && !TRENDING_ROUNDUP.containsMatchIn(headline) },
        "unnamed_demonstrative" to SignalMatcher { headline, context -> if (GALLERY_POINTER.containsMatchIn(headline)) emptyList() else unnamedDemonstrative(headline, context.articleType) },
        "genre_prefix_separator" to regexMatcher(GENRE_PREFIX_SEPARATOR),
        "genre_prefix_subtitle" to SignalMatcher { headline, _ -> genrePrefixSubtitle(headline) },
        "feeling_word_event" to regexMatcher(FEELING_WORD_EVENT),
        "pseudo_reassurance_alarm" to regexMatcher(PSEUDO_REASSURANCE_ALARM),
        "temporal_suspension_threat" to regexMatcher(TEMPORAL_SUSPENSION_THREAT),
        "survival_challenge_frame" to regexMatcher(SURVIVAL_CHALLENGE_FRAME),
        "prescriptive_behavioral_imperative" to SignalMatcher { headline, _ -> prescriptiveBehavioralImperative(headline) },
        "cataphoric_second_person_gap" to regexMatcher(CATAPHORIC_SECOND_PERSON_GAP),
        "advice_dilemma_cliffhanger" to regexMatcher(ADVICE_DILEMMA_CLIFFHANGER) { headline, match ->
            FIRST_PERSON_BEFORE.containsMatchIn(headline.substring(0, match.range.first))
        },
        "prescriptive_deficit_hook" to regexMatcher(PRESCRIPTIVE_DEFICIT_HOOK),
        "unnamed_expert_lead" to regexMatcher(UNNAMED_EXPERT_LEAD),
        "unnamed_way_to_cure" to SignalMatcher { headline, _ -> unnamedWayToCure(headline) },
        "agentless_label" to regexMatcher(AGENTLESS_LABEL),
        "unnamed_group_credit" to regexMatcher(UNNAMED_GROUP_CREDIT),
        "unattributed_report" to regexMatcher(UNATTRIBUTED_REPORT) { headline, _ ->
            !NAMED_OUTLET_CREDIT.containsMatchIn(headline) && !PUBLIC_ACT.containsMatchIn(headline)
        },
        "unnamed_study" to contextGatedMatcher(UNNAMED_STUDY) { !namesStudySource(it) },
        "withheld_reveal_ending" to regexMatcher(WITHHELD_REVEAL_ENDING),
        "announced_answer_closing" to regexMatcher(ANNOUNCED_ANSWER_CLOSING),
        "numbered_payoff" to regexMatcher(NUMBERED_PAYOFF, ::announcedAnswerKept),
        "intrigue_subject" to regexMatcher(INTRIGUE_SUBJECT) { headline, match -> announcedAnswerKept(headline, match) && describesNotNames(headline, match) && !LABELLED_OBJECT.containsMatchIn(headline) },
        "withheld_outcome_hook" to regexMatcher(WITHHELD_OUTCOME_HOOK),
        "withheld_subject_label" to regexMatcher(WITHHELD_SUBJECT_LABEL),
        "pronoun_subject_lead" to regexMatcher(PRONOUN_SUBJECT_LEAD),
        "withheld_verdict" to regexMatcher(WITHHELD_VERDICT, ::announcedAnswerKept),
        "characterised_remark" to regexMatcher(CHARACTERISED_REMARK),
        "leading_question" to regexMatcher(LEADING_QUESTION),
        "sweeping_verdict" to regexMatcher(SWEEPING_VERDICT),
        "insinuation" to regexMatcher(INSINUATION),
        "loaded_label" to regexMatcher(LOADED_LABEL),
        "mockery" to regexMatcher(MOCKERY),
        "horror_lead" to regexMatcher(HORROR_LEAD),
        "emphasis_caps" to regexMatcher(EMPHASIS_CAPS),
        "announced_statement" to regexMatcher(ANNOUNCED_STATEMENT) { headline, match ->
            // "issues statement in support of…" says where the statement stands (flag review 2026-11-f15).
            announcedAnswerKept(headline, match) && !NEWS_GIVEN_FIRST.containsMatchIn(headline.substring(0, match.range.first)) && !STANCE_GIVEN_AFTER.containsMatchIn(headline.substring(match.range.last + 1)) &&
                AUTHORITY_UPDATE.findAll(headline).none { it.range.first <= match.range.first && match.range.first <= it.range.last }
        },
        "reader_dilemma_question" to regexMatcher(READER_DILEMMA_QUESTION),
        "ranked_tease" to regexMatcher(RANKED_TEASE),
        "announced_answer_here" to regexMatcher(ANNOUNCED_ANSWER_HERE),
        "unnamed_professional" to regexMatcher(UNNAMED_PROFESSIONAL),
        "tease_formula" to regexMatcher(TEASE_FORMULA, ::announcedAnswerKept),
        "stated_harm" to regexMatcher(STATED_HARM) { headline, match -> !FUTURE_HARM_CUE.containsMatchIn(headline.substring(0, match.range.first)) },
    )

    /** All signal IDs registered with the detector. */
    val registeredSignalIds: Set<String> get() = REGISTRY.keys

    /**
     * Every place this signal fires, in order.
     *
     * Needed because a signal can be used to *mute part of a headline* rather
     * than to mark it: see the suppression note in [HeadlineFlagger]. Muting
     * has to know about all the attributions in a headline, not just the first:
     * "Starmer says X as Macron warns Y" has two, and stopping at the first
     * would leave the second half of the sentence unmuted.
     */
    fun allMatches(signal: String, headline: String, allCapsAllowlist: Set<String>, articleType: ArticleType? = null): List<MatchResult> =
        allMatches(signal, headline, SignalContext(allCapsAllowlist, articleType))

    /** Every place this signal fires, in order, given what the article says about itself. */
    fun allMatches(signal: String, headline: String, context: SignalContext): List<MatchResult> =
        REGISTRY[signal]?.match(headline, context) ?: emptyList()

    private fun namedAttribution(headline: String): List<MatchResult> =
        NAMED_ATTRIBUTION.findAll(headline).filter { match ->
            val subjectWords = match.groupValues[1].trim().split(WHITESPACE)
            subjectWords.any { it.lowercase() !in VAGUE_ROLE_WORDS }
        }.toList()

    /**
     * A short quoted fragment buried in a longer headline: 'controlling
     * behaviour', 'substances', 'Trojan horse'.
     *
     * These let a paper print a claim without asserting it. The quotes carry all
     * the meaning and none of the responsibility, and the reader usually
     * remembers the claim rather than the punctuation.
     *
     * A headline that is *mostly* a quotation is something else entirely
     * (that's ordinary direct quotation) so the fragment has to be short
     * relative to the headline for this to count.
     *
     * **Not counted: a quoted span that is Title Case throughout.** Real scare
     * quotes are ordinary words made to look sinister: 'controlling
     * behaviour', 'substances'. A Title Case span is a name instead: a show
     * title ('Monster'), a nickname for a piece of hardware ('Flying
     * Chernobyl'), a coined term ('TACO' trade). Quoting a name isn't putting
     * a claim in someone else's mouth, because a name doesn't make a claim.
     */
    private fun scareQuotes(headline: String): List<MatchResult> {
        val words = headline.split(WHITESPACE).size
        if (words < 8) return emptyList()
        return QUOTED.findAll(headline).filter { match ->
            val quotedText = match.groupValues[1].trim()
            val quotedWords = quotedText.split(WHITESPACE)
            // Codebook rule 1: one to four words inside a longer headline. A third
            // of the headline, in whole numbers, refused a 3-word quote in an
            // 11-word headline ("found with 'suicide bomb manual'").
            quotedWords.size <= 4 && words - quotedWords.size >= 4 &&
                !isTitleCase(quotedWords) && quotedText.lowercase() !in SCARE_QUOTE_EXCLUSIONS
        }.toList()
    }

    /**
     * A scare-quoted fragment that carries a dramatic claim nobody in the
     * headline stands behind: "Pilot 'tries to crash' Israel flight", "Suspect
     * foiled RAF Fairford 'bomb plot'", "Antisemitism 'endemic' in British
     * universities". The outlet puts an allegation, a charge or a loaded label
     * in the headline without saying whose it is, which codebook v3 rule 1
     * counts as anonymous sourcing on its own.
     *
     * Not counted here ([scareQuotes] still marks them softly):
     * - a speaker the headline names or identifies by role ([quoteSpeakerNamed]):
     *   "Andy Burnham says there are 'strong indications'", "Yvette Cooper
     *   accuses NHS of treating women as 'second-class patients'",
     *   "construction boss blames 'family tragedies'";
     * - a word that is mentioned rather than used: "the word 'gangs'";
     * - a quotation that opens the headline before a colon or dash;
     * - a press review, where the quotes are other papers' headlines: "The
     *   Papers: 'Giddy King' and 'war of words' over Earl Spencer book";
     * - quoted words with no allegation, charge or loaded label in them:
     *   "'unseasonable' September warm spell", "'devoted' mother", "'one in,
     *   one out' deal".
     */
    private fun dramaticScareQuote(headline: String): List<MatchResult> {
        val dramatic = scareQuotes(headline).filter { DRAMATIC_CLAIM.containsMatchIn(it.groupValues[1]) }
        if (dramatic.isEmpty() || PRESS_REVIEW.containsMatchIn(headline) || quoteSpeakerNamed(headline)) return emptyList()
        // Rule pack 30: an authority in the headline (police, a court, prosecutors) or an
        // interviewee ("Holocaust survivor on 'very dangerous' AfD win") named before the quote is
        // whose words they are; "…'bomb plot' by calling police" names nobody behind it.
        val firstQuote = dramatic.minOf { it.range.first }
        if (AUTHORITY_SPEAKER.findAll(headline).any { it.range.first < firstQuote }) return emptyList()
        return dramatic.filterNot {
            MENTION_CUE.containsMatchIn(headline.substring(0, it.range.first)) || isLeadingQuotation(headline, it.range)
        }
    }

    /**
     * A quotation that opens the headline and is followed by a colon or dash is
     * somebody's words introducing the story ("'Have them all shot': BBC gets
     * complaints over Jeremy Clarkson's comments"), not a scare quote.
     */
    private fun isLeadingQuotation(headline: String, range: IntRange): Boolean =
        headline.substring(0, range.first).isBlank() &&
            (LEADING_QUOTE_FOLLOWER.containsMatchIn(headline.substring(range.last + 1)) ||
                // The colon can sit inside the closing quote: "‘Licence to kill:’ French protests…".
                headline.substring(range).trimEnd('\'', '’', '"', '”').trimEnd().endsWith(':'))

    private val AUTHORITY_SPEAKER = Regex(
        """\b(?:police|detectives|officers|court|judge|jury|jurors|prosecutors?|coroner|inquest|trial|sentencing|jailed|sentenced|convicted)\b|\b\p{L}+\s+on\s+['‘"“]""",
        RegexOption.IGNORE_CASE,
    )

    private val LEADING_QUOTE_FOLLOWER = Regex("""^\s*[:\-–—]""")

    /** A press-review label, under which every quotation is another paper's headline. */
    private val PRESS_REVIEW = Regex(
        """^\W*(?:the\s+)?(?:papers|newspaper\s+headlines|front\s+pages|what\s+the\s+papers\s+say)\s*:""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Words that turn a quoted fragment into framing (codebook v8 rule 1): loaded labels,
     * sneers and alarm characterizations. Reported conduct in quotes ('biting and punching
     * crew', 'stolen' goods, 'bomb plot') is shorthand for "alleged" and no longer counts.
     * Earlier wording, kept for the record: violence and crime,
     * dishonesty and corruption, misconduct and loaded labels, and alarm. A
     * word list can't be complete; this one covers the fragments in the
     * labeled sets and the codebook's examples.
     */
    private val DRAMATIC_CLAIM = Regex(
        """\b(?:""" +
            """fraud(?:ulent|ster)?|scam(?:s|mer)?|corrupt(?:ion)?|cover-?up|li(?:e|es|ar)|sham|propaganda|rigged|hoax|fake|""" +
            """treason|traitors?|betrayal|sleaz(?:e|y)|pervert|predator|pa?edophile|groomer|bull(?:y|ies)|""" +
            """racis(?:t|m)|misogyn(?:y|ist)|homophob(?:ic|ia)|transphob(?:ic|ia)|antisemit(?:ic|ism)|islamophob(?:ic|ia)|""" +
            """bigot(?:ed|ry)?|fascis(?:t|m)|nazis?|thugs?|evil|depraved|deranged|unhinged|extremis(?:t|m)|far-(?:right|left)|""" +
            """woke|invasion|invaders?|illegals|terror(?:ist)?|genocide|massacre|bloodbath|""" +
            """endemic|epidemic|crisis|catastroph(?:e|ic)|disast(?:er|rous)|chaos|chaotic|meltdown|""" +
            """horror|horrific|toxic|sinister|shocking|appalling|disgust(?:ing)?|disgrace(?:ful)?|scandal(?:ous)?|""" +
            """outrage(?:ous)?|fury|furious|rage|humiliat(?:ing|ion)|insane|reckless|tribute""" +
            """)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "the word 'gangs'", "the term 'woke'": a word quoted to talk about the word. */
    private val MENTION_CUE = Regex(
        """\b(?:words?|terms?|phrases?|labels?|slogans?|nicknames?|mottos?|hashtags?|chants?|titles?)\s+$""",
        RegexOption.IGNORE_CASE,
    )

    /** Verbs that report what somebody said, including loaded ones: the speaker is still named. */
    private val SPEECH_VERB = Regex(
        """\b(?:says?|said|tells?|told|warns?|warned|claims?|claimed|insists?|insisted|admits?|admitted|""" +
            """accuses|accused|brands?|branded|calls?|called|labels?|labell?ed|dubs?|dubbed|describes?|described|""" +
            """condemns?|condemned|blames?|blamed|vows?|vowed|denies|denied|urges?|urged|hails?|hailed|praises?|""" +
            """praised|slams?|slammed|blasts?|blasted|mocks?|mocked|defends?|defended|confirms?|confirmed|""" +
            """argues?|argued|declares?|declared|writes?|wrote|recalls?|recalled|recounts?|recounted|remembers?|remembered|""" +
            """reflects?|reflected|saying|claiming|insisting|admitting|accusing|branding|labelling|""" +
            """labeling|describing|condemning|blaming|denying|urging|warning)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Small words that can sit next to a speech verb without being its speaker. */
    private val SPEAKER_FILLER = setOf(
        "a", "an", "the", "and", "as", "of", "to", "in", "on", "at", "for", "with", "his", "her", "their",
        "its", "who", "that", "now", "also", "has", "have", "had", "is", "are", "was", "were", "be", "being",
    )

    /**
     * True when a speech verb in the headline has a speaker who is named or
     * identified by role ("Andy Burnham says", "construction boss blames", "…,
     * says EDF boss"). An anonymous speaker ("pals say", "sources claim") is
     * not identified, so a quote attributed to one is still anonymous sourcing.
     */
    private fun quoteSpeakerNamed(headline: String): Boolean =
        SPEECH_VERB.findAll(headline).any { verb ->
            val before = headline.substring(0, verb.range.first).trimEnd()
            val subject = if (before.endsWith(",")) {
                // "…, says EDF boss": the speaker follows the verb.
                headline.substring(verb.range.last + 1).trim().split(WHITESPACE).take(4)
            } else {
                before.split(WHITESPACE).takeLast(3)
            }
            subject.map { it.trim('\'', '"', ',', '.', ':', ';', '!', '?').lowercase() }
                .filter { it.isNotEmpty() && it !in SPEAKER_FILLER }
                .any { it !in VAGUE_ROLE_WORDS }
        }

    /** True when every word in a quoted span starts with a capital: a name, not a phrase. */
    private fun isTitleCase(words: List<String>): Boolean =
        words.isNotEmpty() && words.first().let { it.isNotEmpty() && it.first().isUpperCase() } &&
            words.all { it.isNotEmpty() && (it.first().isUpperCase() || it.lowercase() in TITLE_MINOR_WORDS) }

    /** Small words a title keeps in lower case: 'Pride and Prejudice', 'Lord of the Rings'. */
    private val TITLE_MINOR_WORDS = setOf("a", "an", "and", "the", "of", "to", "in", "on", "at", "for", "or", "but", "with", "from", "by", "nor", "as", "vs")

    /**
     * An isolated uppercase word (3+ chars) used for emotional shouting or artificial emphasis in tabloids
     * ("REALLY", "VERY", "BLADE", "WEEK", "DUCT-TAPED"), excluding common acronyms, roman numerals,
     * and leading bylines or broadcast labels ("SARAH VINE:", "WATCH:").
     */
    private val MID_CAPS_WORD = Regex("""(?<![\p{L}\p{N}])[A-Z]{3,}(?:-[A-Z]{2,})*(?![\p{L}\p{N}])""")
    private val BYLINE_OR_TAG_PREFIX = Regex("""^[A-Z\s'.-]{3,}:""")

    /**
     * Trailing author byline or credit at the end of a headline:
     * ": ALAN DERSHOWITZ", "| JONATHAN WILSON", "-- MAUREEN CALLAHAN", "- BY SUELLA BRAVERMAN".
     * Words in these bylines are personal names, not urgency shouting.
     */
    private val TRAILING_BYLINE = Regex(
        """(?::\s*|\s*\|\s*|\s+--\s+|\s+-\s+(?:by\s+)?|,\s+(?:reveals?|writes?|reports?|says?)\s+)([A-Z][\p{L}'.-]*(?:\s+[A-Z][\p{L}'.-]*){0,3})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val NON_BYLINE_WORDS = setOf(
        "WHY", "WHAT", "HOW", "HERE", "WHEN", "WHERE", "WHO", "WHICH",
        "WATCH", "LIVE", "VIDEO", "PICTURES", "UPDATE", "EXCLUSIVE",
        "NEWS", "REPORT", "ANALYSIS", "FACTS", "TRUTH", "REASON", "MATTERS"
    )

    private fun trailingBylineStart(headline: String): Int {
        val suffix = TRAILING_BYLINE.find(headline) ?: return Int.MAX_VALUE
        val bylineText = suffix.groupValues[1].trim()
        val words = bylineText.split(WHITESPACE).map { it.uppercase(java.util.Locale.US) }
        val isTeaser = words.any { it in TABLOID_SHOUT_WORDS || it in NON_BYLINE_WORDS }
        return if (isTeaser) Int.MAX_VALUE else suffix.range.first
    }

    private val STANDARD_ACRONYMS = setOf(
        "UK", "US", "USA", "EU", "UN", "BBC", "NHS", "AI", "DNA", "WFH",
        "MP", "PM", "NATO", "NASA", "FBI", "CIA", "COVID", "HRH", "RAF",
        "HIV", "DWTS", "GOP", "NATS", "MLB", "NFL", "NBA", "NHL", "FIFA",
        "UEFA", "UFC", "F1", "GDP", "CPI", "IRS", "SEC", "DOJ", "DNC",
        "RNC", "DEI", "IDF", "WHO", "IMF", "CEO", "CFO", "CTO", "CIO",
        "COO", "SUV", "EV", "UFO", "PR", "IVF", "ADHD", "PTSD", "LGBT",
        "LGBTQ", "LGBTQ+", "COP28", "COP29", "WW1", "WW2", "WWI", "WWII",
        "II", "III", "IV", "VI", "VII", "VIII", "IX", "XI", "XII",
        "NYC", "LA", "DC", "NSW", "ACT", "WA", "QLD", "SA", "TAS", "NT",
        "NZ", "AUS", "CAN", "IRE", "RSA", "UAE", "KSA", "IOC", "ICC",
        "VAR", "TKO", "KO", "PGA", "ATP", "WTA", "NASCAR", "BOM", "NWS",
        "FAA", "TSA", "NTSB", "EPA", "FDA", "CDC", "NIH", "DOD", "DHS",
        "CBP", "ICE", "ATF", "NSA", "DEA", "FTC", "FCC", "OSHA", "EEOC",
        "FEC", "CPSC", "CFPB", "NLRB", "GAO", "OMB", "CBO", "MI5", "MI6",
        "GCHQ", "MOD", "HMRC", "DWP", "CPS", "NCA", "DVLA", "TfL", "HS2",
        "GB", "AMG", "RWD", "FWD", "AWD", "CLE", "BMW", "VW", "GM", "RAM", "GMC", "BYD",
        "ABC", "DVD", "NBCLA", "USDA", "UNC", "MVP", "IAEA", "DOGE", "FRONTIER", "GLP",
        "ODI", "T20", "IPL", "WPL", "ECB", "BOE", "RSPCA", "RNLI", "CBI", "TUC",
        "SNP", "DUP", "BMA", "RMT", "FTSE", "AIM", "IPO", "LSE", "NYSE", "NASDAQ",
        "ITV", "OFCOM", "OFWAT", "OFSTED", "OFGEM"
    )

    private val TABLOID_SHOUT_WORDS = setOf(
        "REALLY", "VERY", "MORE", "ALL", "NEVER", "EVER", "MUST", "NOW", "THIS", "WHY",
        "SHOCK", "JUST", "HUGE", "MAJOR", "ONLY", "WORST", "BEST", "BLADE", "SIXTH",
        "FIRST", "LAST", "DEAD", "NOT", "OUT", "BACK", "UP", "DOWN", "ONE", "TWO", "THREE",
        "STOP", "SAVE", "DANGER", "SECRET", "TRUE", "REAL", "NEW", "RAW", "PURE"
    )

    /**
     * Short words written in capitals for emphasis ("Chick Fil A Did WHAT",
     * "Yes, YOUR team", "a BIG surprise"). Any other capitalized word of three
     * or four letters is read as an acronym: in the tuning data those were CNBC,
     * NCAA, TDS, BMO, NBC, ESPN, GTA and ISP, and no allowlist keeps up with
     * them. Longer words still count ("just HOURS before", "SLATES the king").
     */
    private val SHORT_SHOUT_WORDS = setOf(
        "THE", "AND", "BUT", "FOR", "YOU", "YOUR", "THAT", "WHAT", "HOW", "WHEN",
        "HERE", "THEY", "THEM", "SHE", "HER", "HIM", "HIS", "HAS", "WAS", "ARE", "WILL",
        "ALSO", "EVEN", "MUCH", "MOST", "LIKE", "LOVE", "HATE", "OVER", "SOON", "GONE",
        "FREE", "FAKE", "LIES", "KILL", "DIED", "DIES", "BIG", "BAD", "SAD", "MAD", "HOT",
        "EPIC", "WILD", "SICK", "WOW", "OMG", "YES", "ANY", "OFF", "SAME",
    )

    /**
     * Two to four capitalized words in a row with no shout word among them are a
     * name set in capitals, as the Daily Mail does mid-headline ("Former Vogue
     * editor ALEXANDRA SHULMAN reveals", "DR PHILIPPA KAYE reveals"), not
     * shouting. A longer run is an all-caps headline and is judged word by word.
     */
    private val CAPS_RUN = Regex("""(?<![\p{L}\p{N}])[A-Z][A-Z'.-]+(?:\s+[A-Z][A-Z'.-]+)+(?![\p{L}\p{N}])""")
    private val ROMAN_NUMERAL = Regex("""M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})""")

    private fun capsNames(headline: String): List<IntRange> =
        CAPS_RUN.findAll(headline).filter { run ->
            val words = run.value.split(WHITESPACE)
            words.size <= 4 && words.none { it in TABLOID_SHOUT_WORDS || it in SHORT_SHOUT_WORDS }
        }.map { it.range }.toList()

    private fun looksShouted(word: String): Boolean = when {
        word in TABLOID_SHOUT_WORDS || word in SHORT_SHOUT_WORDS -> true
        word.length < 5 -> false
        else -> !ROMAN_NUMERAL.matches(word)
    }

    private fun midHeadlineCaps(headline: String, allowlist: Set<String>): List<MatchResult> {
        val bylinePrefix = BYLINE_OR_TAG_PREFIX.find(headline)
        val prefixEnd = bylinePrefix?.range?.last ?: -1
        val bylineStart = trailingBylineStart(headline)
        val names = capsNames(headline)
        return MID_CAPS_WORD.findAll(headline).filter { match ->
            match.range.first > prefixEnd &&
                match.range.last < bylineStart &&
                match.value !in STANDARD_ACRONYMS &&
                match.value !in allowlist &&
                looksShouted(match.value) &&
                // "[SPOILER]", "[UPDATED]": an editor's tag, not shouting.
                !(headline.getOrNull(match.range.first - 1) == '[' && headline.getOrNull(match.range.last + 1) == ']') &&
                names.none { it.overlaps(match.range) }
        }.toList()
    }

    private fun tabloidShoutCaps(headline: String, allowlist: Set<String>): List<MatchResult> {
        val bylinePrefix = BYLINE_OR_TAG_PREFIX.find(headline)
        val prefixEnd = bylinePrefix?.range?.last ?: -1
        val bylineStart = trailingBylineStart(headline)
        val names = capsNames(headline)
        return MID_CAPS_WORD.findAll(headline).filter { match ->
            match.range.first > prefixEnd &&
                match.range.last < bylineStart &&
                match.value !in STANDARD_ACRONYMS &&
                match.value !in allowlist &&
                (match.value in TABLOID_SHOUT_WORDS || match.value.contains('-')) &&
                names.none { it.overlaps(match.range) }
        }.toList()
    }
}

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

internal class SpanMatchResult(
    override val range: IntRange,
    override val value: String,
) : MatchResult {
    override val groupValues: List<String> get() = listOf(value)
    override val groups: MatchGroupCollection
        get() = throw UnsupportedOperationException("groups not supported on SpanMatchResult")
    override fun next(): MatchResult? = null
}

