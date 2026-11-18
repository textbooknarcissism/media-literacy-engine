package com.medialiteracy.flagging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignalsTest {

    private val defaultAllowlist = setOf(
        "US", "UK", "EU", "UN", "FBI", "CIA", "NASA", "NHS", "NATO", "BBC", "ITV", "CNN", "MSNBC",
        "GOP", "DNC", "PM", "MP", "CEO", "CFO", "CTO", "AI", "EV", "GLP", "GDP", "CPI", "NTSB",
        "FAA", "FDA", "CDC", "EPA", "SEC", "DOJ", "IRS", "COP", "COVID", "mRNA", "UFC", "NBA", "NFL", "MLB",
    )

    @Test
    fun `all signals referenced in rulepack-v1 json exist in Signals registry`() {
        val pack = RulePackLoader.loadDefault()
        val registered = Signals.registeredSignalIds

        val referencedSignals = mutableSetOf<String>()
        for (cat in pack.categories) {
            referencedSignals.addAll(cat.signals)
            referencedSignals.addAll(cat.noticeSignals)
            referencedSignals.addAll(cat.softSignals)
            referencedSignals.addAll(cat.cancelSignals)
            referencedSignals.addAll(cat.muteSignals)
        }

        val missing = referencedSignals - registered
        assertTrue(
            missing.isEmpty(),
            "rulepack-v1.json references signals that are not in Signals.registeredSignalIds: $missing"
        )
    }

    @Test
    fun `every registered signal has a positive match on expected input()`() {
        // Map of signal ID to sample headline guaranteed to trigger it
        val positiveCases = mapOf(
            "multiple_exclamations" to "Unbelievable news!! Check this out",
            "numeric_claim" to "Company profits grew by 25 percent this quarter",
            "teaser_prefix" to "Unmasked: the reality star behind the secret romance",
            "question_headline" to "Could interest rates drop next month?",
            "scare_quotes" to "Ministers defend their controversial 'solution' to the ongoing railway crisis across Britain",
            "trailing_sources_credit" to "Bank chiefs to attend state dinner at the White House: Sources",
            "quoted_speech" to "Amanda Redman: 'My most embarrassing moment? Getting caught doing a pretend Bafta speech'",
            "labelled_opinion" to "Sham City: verdict on financial breaches is damning and punishment must be severe | Jonathan Wilson",
            "breaking_label" to "Breaking: Market falls sharply following announcement",
            "just_in_label" to "Just In: Court reaches verdict in landmark case",
            "named_attribution" to "Starmer says new policy will lower energy bills",
            "literal_event_or_alert" to "Officials raise terror threat level to critical following security review",
            "mid_headline_caps" to "New laws mean HUGE penalties for landlords",
            "second_person_threat" to "Warning: your savings are in danger under new banking rules",
            "leading_exclamation_hook" to "Look! Stunned onlookers watch as meteor streaks across sky",
            "rhetorical_question_hook" to "Think your pension is safe? Think again",
            "teaser_profile_lead" to "Inside the secretive billionaire's life and sudden fall",
            "celebrity_reaction_formula" to "Travis Kelce Reacts To Dating Rumors",
            "withheld_statement" to "Actor breaks silence on divorce",
            "staged_exchange" to "Lily Allen responds to David Harbour's comments about her album",
            "dramatic_scare_quote" to "Council's 'woke' ban on Christmas lights leaves town square in darkness",
            "feeling_word_event" to "Two dead after horror crash between camper van and lorry",
            "two_sentence_cliffhanger" to "He walked into the bank. It's only the start of the nightmare",
            "conflict_staging" to "Rebel MP hits out at minister over tax hike plans",
            "tabloid_shout_caps" to "Furious drivers demand HUGE action over pothole nightmare",
            "betteridge_superlative_question" to "Could this really be the world's worst movie ever made?",
            "viral_emotional_imperative" to "This heartbreaking rescue will make you cry",
            "ingroup_identity_hook" to "Only 90s kids will remember these snacks",
            "cataphoric_deictic_lead" to "This is the story of how a small town beat a tech giant",
            "quiz_self_id_formula" to "Which character from Friends are you based on your coffee order?",
            "viral_prescriptive_command" to "You need to see this incredible performance right now",
            "viral_listicle_hook" to "17 Genius hacks you wish you knew sooner",
            "we_know_based_on" to "We know what your secret talent is based on your birth month",
            "challenge_quiz_prompt" to "Can you guess which of these celebrities went to college?",
            "how_well_do_you" to "How well do you remember the 1990s world cup?",
            "what_would_your_be" to "What would your dream vacation be based on your birth month?",
            "superlative_listicle_lead" to "25 of the most ridiculous fashion trends from the 2000s",
            "cliffhanger_consequence" to "She opened the door and broke down in tears",
            "second_person_viral_hacks" to "10 Kitchen hacks you never knew existed",
            "deictic_viral_subject" to "This guy filmed a music video in a grocery store",
            "viral_first_person_lead" to "We need to talk about what happened on the season finale",
            "perfect_response_formula" to "The internet has the perfect response to airline fee hike",
            "viral_meme_frame" to "When you just can't deal with Monday mornings",
            "sports_fury" to "Tyson Fury delivers explosive warning to Joshua ahead of rematch",
            "accusatory_habit_listicle" to "5 Mistakes you are probably making when buying a used car",
            "withheld_discovery_hook" to "Scientists just discovered a massive structure under Antarctica",
            "tell_all_why_hook" to "Hollywood star reveals why she stepped away from acting",
            "speculative_financial_cataphora" to "These stocks could surge in November",
            "trailing_rhetorical_question" to "Tony Blair would like Andy Burnham to take his advice on rejoining the EU. Should he? | Tom Baldwin",
            "broadsheet_feature_why" to "Why couples are getting divorced later in life",
            "imperative_reaction_hook" to "Hear panelist's reaction to the breaking news",
            "cataphoric_threshold" to "Treasury yields could rattle investors if they reaches this level",
            "withheld_answer_here" to "I asked 5 experts whether the plan would work. Here's what they said",
            "unnamed_demonstrative" to "This semiconductor stock is in the dumps. BMO says it can bounce back",
            "genre_prefix_separator" to "Star Wars Galactic Racer review: now this is frenetically fun podracing",
            "genre_prefix_subtitle" to "Star Wars Galactic Racer review: now this is frenetically fun podracing",
            "pseudo_reassurance_alarm" to "You Probably Aren't Going to Get the Plague",
            "temporal_suspension_threat" to "The electric grid won't collapse this winter (for now)",
            "survival_challenge_frame" to "Can your body survive the summer heatwave?",
            "prescriptive_behavioral_imperative" to "Why you should never charge your phone overnight",
            "cataphoric_second_person_gap" to "The one detail you probably missed in the season finale",
            "advice_dilemma_cliffhanger" to "My financial adviser wants me to invest $350k in UAE data center. Is this risky?",
            "prescriptive_deficit_hook" to "Cardinal football is back - here's what you need to know",
            "unnamed_expert_lead" to "Economists warn the recovery has stalled",
            "agentless_label" to "New parking rules are branded a 'nightmare'",
            "unnamed_way_to_cure" to "Scientists find a way to fight Alzheimer's brain damage without removing toxic tau",
            "unnamed_group_credit" to "Bridge repairs will take another year, officials say",
            "unattributed_report" to "Report: Club set to sack manager after derby defeat",
            "unnamed_study" to "Coffee drinkers sleep less on weekends, study finds",
            "withheld_reveal_ending" to "Pop star's real age revealed",
            "announced_answer_closing" to "The new tax year starts next week. Here's everything we know.",
            "numbered_payoff" to "I ran a marathon at 60. Here are 5 things I learned",
            "intrigue_subject" to "One simple habit can cut your energy bill",
            "withheld_outcome_hook" to "The mayor went to a school fete and did something nobody expected",
            "withheld_subject_label" to "Major update after man found in canal",
            "pronoun_subject_lead" to "She Left Her Job to Sail Around the World",
            "withheld_verdict" to "Former captain gives verdict on penalty decision",
            "characterised_remark" to "Senator says rival's 'grow up' comment shows his contempt for voters",
            "leading_question" to "Is remote work really over?",
            "loaded_premise_question" to "Was the coach right to drop his captain?",
            "sweeping_verdict" to "This tiny phone has changed everything",
            "insinuation" to "Council quietly scraps cycle lane plan",
            "loaded_label" to "Tech oligarchs line up for tax cut",
            "mockery" to "So much for the green revolution",
            "horror_lead" to "Horror crash closes motorway for hours",
            "emphasis_caps" to "Council tax bills to almost DOUBLE next year",
            "announced_statement" to "Singer drops statement after festival row",
            "reader_dilemma_question" to "My brother never pays me back. Should I lend him more?",
            "ranked_tease" to "I tried five supermarket pizzas. The winner was a surprise.",
            "announced_answer_here" to "Energy prices are rising again. Here's what to buy now",
            "unnamed_professional" to "Dermatologists reveal the sunscreen mistake to avoid",
            "tease_formula" to "The new phone is finally here, but there's a catch",
            "intrigue_slot" to "Inmate has special request before sentencing",
            "counted_slot" to "Fans spot one controversial detail in his kitchen",
            "negated_guess" to "Series renewed for a third season, but not the one you think",
            "reaction_what" to "Mum rips up carpet and can't get over what pours out",
            "fond_descriptor" to "Popular supplement linked to faster memory loss",
            "named_secret" to "This sunscreen is the secret to glowy skin",
            "verbal_demolition" to "Senator destroys rival in fiery debate",
            "same_answer" to "I asked six vets the best dog food and they all said the same thing",
            "what_clause" to "Plumber called to flat is stunned by what was inside the wall",
            "unstated_reason" to "Supermarket pulls its own-brand butter for a surprising reason",
            "withheld_attribute" to "Studio confirms sequel release date",
            "generic_subject" to "Major airline to scrap free hand luggage next month",
            "vague_big_change" to "Major change coming to high street banks this spring",
            "counted_and_kept" to "The chancellor just said 7 words that rattled the markets",
            "hook_aside" to "The new rail pass is half price, but of course there's a catch",
            "then_twist" to "Man orders soup at airport cafe. Then he sees the bill",
            "bare_this" to "Add a spoonful of this to your porridge every morning",
            "fond_possessive" to "Supermarket to close its much-loved cafe",
            "pointed_person" to "Why this county sheriff thinks the new jail will fail",
            "announced_list" to "The reasons a third runway may never be built",
            "closing_teaser" to "The town was promised a new hospital. That might be over.",
            "recalled_descriptor" to "A phone we once called the best of its year is being withdrawn",
            "promised_explanation" to "The new stadium is a mistake, and I'll tell you why",
            "these_are_the_most" to "Flying home for Christmas? These are the cheapest days to book",
            "stated_reveal" to "Studio reveals the sequel has sold two million copies",
            "aged_unnamed_rule" to "Fishing crews are testing a little-known maritime law",
            "borrowed_name_condition" to "New pill tested for same condition as Bruce Willis",
            "crowd_credit" to "Presenter's exit plan 'rumbled' by viewers",
            "unnamed_poll" to "New poll shows mayor trailing in every ward",
            "unspoken_quotes" to "Striker faces 'nightmare' week as deadline nears – 'time is running out'",
            "disclosed_slot" to "Actor reveals his daughter's reaction to the film",
            "slot_revealed" to "Singer's romance with co-star revealed",
            "stated_harm" to "Two dead after horror crash on the M6",
        )

        for (signal in Signals.registeredSignalIds) {
            val headline = positiveCases[signal]
            assertNotNull(headline, "Missing positive test case for registered signal '$signal'")
            val matches = Signals.allMatches(signal, headline, defaultAllowlist)
            assertTrue(
                matches.isNotEmpty(),
                "Expected signal '$signal' to match '$headline', but got 0 matches"
            )
        }
    }

    @Test
    fun `tabloid shout caps respects acronym allowlist`() {
        // Whitelisted acronyms should NOT trigger tabloid_shout_caps
        val cleanAcronyms = "NASA and FBI launch joint investigation with UK officials"
        val acronymMatches = Signals.allMatches("tabloid_shout_caps", cleanAcronyms, defaultAllowlist)
        assertTrue(acronymMatches.isEmpty(), "Expected allowlisted acronyms to be clean, got: $acronymMatches")

        // Genuine shout caps should trigger
        val shouting = "Labour's SHOCK tax plan sparks outrage across country"
        val shoutingMatches = Signals.allMatches("tabloid_shout_caps", shouting, defaultAllowlist)
        assertEquals(1, shoutingMatches.size)
        assertEquals("SHOCK", shoutingMatches.first().value)
    }

    @Test
    fun `numeric claim ignores prices rarity and ancient dates`() {
        // Price should NOT trigger numeric_claim
        val price = "Historic £3.2million Scottish castle goes on sale"
        val priceMatches = Signals.allMatches("numeric_claim", price, defaultAllowlist)
        assertTrue(priceMatches.isEmpty(), "Price should not be a numeric claim: $priceMatches")

        // Dollar and Euro prices should also be ignored
        val dollarPrice = "Tech firm buys $500million headquarters in Austin"
        assertTrue(Signals.allMatches("numeric_claim", dollarPrice, defaultAllowlist).isEmpty())

        // Rarity phrase "one in X" should not trigger
        val rarity = "Mother celebrates birth of one in 160 million quadruplets"
        assertTrue(Signals.allMatches("numeric_claim", rarity, defaultAllowlist).isEmpty())

        // Ancient history "years ago" should not trigger
        val ancient = "Fossil discovered from 240million years ago in Argentina"
        assertTrue(Signals.allMatches("numeric_claim", ancient, defaultAllowlist).isEmpty())

        // Actual performance / multiplier claims SHOULD trigger
        val performance = "New quantum chip runs 10 times faster than predecessors"
        val perfMatches = Signals.allMatches("numeric_claim", performance, defaultAllowlist)
        assertEquals(1, perfMatches.size)
        assertTrue(perfMatches.first().value.contains("10 times"))
    }

    @Test
    fun `betteridge superlative question isolates clickbait questions from straight news`() {
        val clickbait = "Could this really be the world's worst movie ever made?"
        val matches = Signals.allMatches("betteridge_superlative_question", clickbait, defaultAllowlist)
        assertEquals(1, matches.size)

        val straightNews = "What did the Chancellor announce in today's autumn statement?"
        val cleanMatches = Signals.allMatches("betteridge_superlative_question", straightNews, defaultAllowlist)
        assertTrue(cleanMatches.isEmpty(), "Straight journalistic question should not trigger Betteridge check")
    }

    @Test
    fun `scare quotes isolates manipulative punctuation from genuine direct quotes`() {
        // Single loaded word in quotes in a headline of 8+ words
        val loaded = "Ministers defend their controversial 'solution' to the ongoing railway crisis across Britain"
        val loadedMatches = Signals.allMatches("scare_quotes", loaded, defaultAllowlist)
        assertEquals(1, loadedMatches.size)
        assertEquals("'solution'", loadedMatches.first().value)

        // Multiple words that form a genuine statement / quote should be ignored
        val statement = "Minister says 'we will not raise taxes on working families' in major policy speech"
        val statementMatches = Signals.allMatches("scare_quotes", statement, defaultAllowlist)
        assertTrue(statementMatches.isEmpty(), "Full quote statement should not be flagged as scare quotes")
    }

    @Test
    fun `named attribution distinguishes individuals from vague institutional roles`() {
        val named = "Starmer says growth remains top economic priority"
        val namedMatches = Signals.allMatches("named_attribution", named, defaultAllowlist)
        assertEquals(1, namedMatches.size)

        // Vague role attributions are NOT named attribution
        val vague = "Insiders say talks are continuing behind closed doors"
        val vagueMatches = Signals.allMatches("named_attribution", vague, defaultAllowlist)
        assertTrue(vagueMatches.isEmpty(), "Vague roles (insiders) must not match named attribution")
    }

    @Test
    fun `a demonstrative pointing back at an earlier sentence withholds nothing`() {
        // From the 2026-10 flag review (rule pack 32).
        val back = "I made Ina Garten's flavorful mashed potatoes. From now on, I'll bring this easy side dish to every dinner party."
        assertTrue(Signals.allMatches("unnamed_demonstrative", back, defaultAllowlist).isEmpty())
        // Opening its own sentence, it still points forward; an unnamed product is still withheld.
        assertEquals(1, Signals.allMatches("unnamed_demonstrative", "Doctors hate him. This one trick will change your life", defaultAllowlist).size)
        assertEquals(1, Signals.allMatches("unnamed_demonstrative", "These Mini Backpacks Hold More Than You Think", defaultAllowlist).size)
        val list = "I've visited over 50 cities in Europe. London and Paris were great, but these 5 lesser-known locations are worth a visit."
        assertEquals(1, Signals.allMatches("unnamed_demonstrative", list, defaultAllowlist).size, "a numbered list points forward")
    }

    @Test
    fun `unnamed scientists reporting a finding are not anonymous sourcing`() {
        // Codebook v11: judged as science instead. A view or a warning still borrows authority.
        for (headline in listOf("Scientists Discover a Strange Molecule in Deep-Sea Mud", "Coffee may protect the liver, researchers find")) {
            for (signal in listOf("unnamed_expert_lead", "unnamed_group_credit")) {
                assertTrue(Signals.allMatches(signal, headline, defaultAllowlist).isEmpty(), "$signal on '$headline'")
            }
        }
        assertEquals(1, Signals.allMatches("unnamed_expert_lead", "Scientists warn the Gulf Stream is slowing", defaultAllowlist).size)
        assertEquals(1, Signals.allMatches("unnamed_group_credit", "Coffee may protect the liver, researchers say", defaultAllowlist).size)
        // A cure-style claim with no stage is early science; a stated stage is honest.
        assertEquals(1, Signals.allMatches("unnamed_way_to_cure", "Scientists discover a way to kill dangerous gut bacteria without killing the good", defaultAllowlist).size)
        assertTrue(Signals.allMatches("unnamed_way_to_cure", "Scientists find a way to slow dementia in mice", defaultAllowlist).isEmpty())
    }

    @Test
    fun `what the record shows settles sourcing`() {
        // Codebook v12: a public act borrows nothing from a "Reports" credit.
        val court = "Judge orders Trump to sit for deposition in BBC defamation lawsuit by Dec. 11: Reports"
        assertTrue(Signals.allMatches("unattributed_report", court, defaultAllowlist).isEmpty())
        assertEquals(1, Signals.allMatches("unattributed_report", "Club set to sack manager after derby defeat: Reports", defaultAllowlist).size)
        // A passive label with nobody behind it is anonymous; naming the speaker settles it.
        assertEquals(1, Signals.allMatches("agentless_label", "Airbnb bans hosts from demanding guests strip beds - as new rules are branded a 'nightmare'", defaultAllowlist).size)
        assertTrue(Signals.allMatches("agentless_label", "Minister was branded a liar by Starmer", defaultAllowlist).isEmpty())
        // A closure of a space or programme is literal, not conflict.
        assertEquals(1, Signals.allMatches("literal_event_or_alert", "Microsoft shuts down its beloved maker space", defaultAllowlist).size)
        // "You probably don't have" is not a gap in what you know.
        val pun = "I know you probably don't have mushroom in your backlog, but fungus looter shooter Mycopunk is now out in 1.0"
        assertTrue(Signals.allMatches("cataphoric_second_person_gap", pun, defaultAllowlist).isEmpty())
        assertEquals(1, Signals.allMatches("cataphoric_second_person_gap", "Things you probably didn't know about your fridge", defaultAllowlist).size)
    }

    @Test
    fun `forward demonstratives announce an answer but temporal phrases are clean`() {
        // "This is why..." is an announced answer (codebook v3 rule 5).
        val tease = "This is why you should never store tomatoes in the fridge"
        assertEquals(1, Signals.allMatches("withheld_answer_here", tease, defaultAllowlist).size)
        assertEquals(1, Signals.allMatches("cataphoric_deictic_lead", "This is the story of how a small town beat a tech giant", defaultAllowlist).size)

        val straight = "This morning lawmakers gathered for the opening session of parliament"
        for (signal in listOf("cataphoric_deictic_lead", "withheld_answer_here")) {
            assertTrue(Signals.allMatches(signal, straight, defaultAllowlist).isEmpty(), "Temporal 'This morning' should not trigger $signal")
        }
    }

    @Test
    fun `literal event or alert matches physical collapses but excludes sensational horror headlines`() {
        // Concrete physical disaster reports should trigger literal_event_or_alert
        val collapse = "Bridge collapse leaves several injured after severe weather"
        val matches = Signals.allMatches("literal_event_or_alert", collapse, defaultAllowlist)
        assertEquals(1, matches.size)
        assertEquals("Bridge collapse", matches.first().value)

        val building = "Historic building collapses overnight in city center"
        val bMatches = Signals.allMatches("literal_event_or_alert", building, defaultAllowlist)
        assertEquals(1, bMatches.size)
        assertEquals("building collapses", bMatches.first().value)

        // Preceded by sensationalist "horror" must NOT be treated as a literal alert
        val horror = "Horror building collapse leaves several injured"
        val horrorMatches = Signals.allMatches("literal_event_or_alert", horror, defaultAllowlist)
        assertTrue(horrorMatches.isEmpty(), "Sensational 'horror building collapse' must not match literal event: $horrorMatches")

        val horrorCrane = "Horror crane collapse in busy shopping district"
        val hcMatches = Signals.allMatches("literal_event_or_alert", horrorCrane, defaultAllowlist)
        assertTrue(hcMatches.isEmpty(), "Sensational 'horror crane collapse' must not match literal event: $hcMatches")
    }

    @Test
    fun `short capitals are acronyms and a run of capitals is a name`() {
        listOf(
            "Anthropic and OpenAI hunt for data centre deals, sources tell CNBC",
            "Cringe alert: TDS celebs pledge oath ahead of midterms",
            "Former DSA insider says the group has changed",
            "1 of these 12 NFL playoff teams will win Super Bowl XLIX",
            "Super Bowl LVIII halftime show line-up confirmed",
            "Former Vogue editor ALEXANDRA SHULMAN reveals how she learned to cope",
            "My confession on heartburn drugs: DR PHILIPPA KAYE reveals the side effects",
        ).forEach { headline ->
            val matches = Signals.allMatches("mid_headline_caps", headline, emptySet())
            assertTrue(matches.isEmpty(), "Expected no shouting in '$headline', got: ${matches.map { it.value }}")
        }
        mapOf(
            "Chick Fil A Did WHAT After the shooting?" to "WHAT",
            "Yes, YOUR team is going to lose" to "YOUR",
            "Son seen just HOURS before his death" to "HOURS",
            "Earl Spencer SLATES the king over memoir" to "SLATES",
        ).forEach { (headline, word) ->
            val matches = Signals.allMatches("mid_headline_caps", headline, emptySet())
            assertEquals(listOf(word), matches.map { it.value }, headline)
        }
    }

    @Test
    fun `mid headline caps ignores standard acronyms like ODI and T20`() {
        val cricket = "England demolish Sri Lanka in ODI series decider as Banton scores ton"
        val matches = Signals.allMatches("mid_headline_caps", cricket, emptySet())
        assertTrue(matches.isEmpty(), "ODI should be recognized as a standard acronym, not shouting: $matches")
    }

    @Test
    fun `literal event or alert matches sports scoring like bashes maiden ton but not political conflict`() {
        val cricket = "England demolish Sri Lanka in ODI series decider as Banton bashes maiden ton"
        val matches = Signals.allMatches("literal_event_or_alert", cricket, defaultAllowlist)
        assertEquals(1, matches.size)
        assertEquals("bashes maiden ton", matches.first().value)

        val politics = "Starmer bashes Tories over public transport budget"
        val polMatches = Signals.allMatches("literal_event_or_alert", politics, defaultAllowlist)
        assertTrue(polMatches.isEmpty(), "Political conflict 'bashes Tories' must not be muted as a literal event")
    }

    @Test
    fun `literal event or alert matches natural disasters, strikes, shutdowns, and celebration bashes`() {
        // Natural disasters
        val hurricane = "Hurricane destroys hundreds of homes in Florida"
        val hMatches = Signals.allMatches("literal_event_or_alert", hurricane, defaultAllowlist)
        assertEquals(1, hMatches.size)
        assertEquals("Hurricane destroys hundreds of homes", hMatches.first().value)

        val celebDestroys = "Celebrity destroys rival in fiery podcast interview"
        assertTrue(Signals.allMatches("literal_event_or_alert", celebDestroys, defaultAllowlist).isEmpty())

        // Labor strikes
        val strike = "Workers strike at Amazon over pay and working hours"
        val sMatches = Signals.allMatches("literal_event_or_alert", strike, defaultAllowlist)
        assertEquals(1, sMatches.size)
        assertEquals("Workers strike at Amazon over", sMatches.first().value)

        val polStrike = "Candidate strikes at opponent over tax policy"
        assertTrue(Signals.allMatches("literal_event_or_alert", polStrike, defaultAllowlist).isEmpty())

        // Industrial shutdowns
        val plant = "Ford shuts down Michigan plant amid supply shortage"
        val pMatches = Signals.allMatches("literal_event_or_alert", plant, defaultAllowlist)
        assertEquals(1, pMatches.size)
        assertEquals("shuts down Michigan plant", pMatches.first().value)

        val debateShutdown = "Senator shuts down critic in tense committee hearing"
        assertTrue(Signals.allMatches("literal_event_or_alert", debateShutdown, defaultAllowlist).isEmpty())

        // Celebration bash
        val birthday = "Royal family attends star-studded birthday bash in London"
        val bMatches = Signals.allMatches("literal_event_or_alert", birthday, defaultAllowlist)
        assertEquals(1, bMatches.size)
        assertEquals("birthday bash", bMatches.first().value)

        val rantBash = "Host bashes guest during controversial episode"
        assertTrue(Signals.allMatches("literal_event_or_alert", rantBash, defaultAllowlist).isEmpty())
    }

    @Test
    fun `trailing rhetorical question matches debate nudges but ignores straight news questions`() {
        fun match(headline: String) =
            Signals.allMatches("trailing_rhetorical_question", headline, emptySet()).map { it.value }

        assertEquals(
            listOf("Should he?"),
            match("Tony Blair would like Andy Burnham to take his advice on rejoining the EU. Should he? | Tom Baldwin"),
        )
        assertEquals(
            listOf("Is it too little, too late?"),
            match("Kamala Harris is belatedly courting progressives. Is it too little, too late? | Arwa Mahdawi"),
        )
        assertEquals(
            listOf("Don't they have more pressing issues?"),
            match("Meryl Streep's casting as Aslan has angered the Christian right. Don't they have more pressing issues?"),
        )
        assertEquals(
            listOf("are you feeling jealous, Vladimir?"),
            match("Trump swoons over strongman soulmate Xi - are you feeling jealous, Vladimir?"),
        )
        assertEquals(
            listOf("What are the NCAA and universities going to do about it?"),
            match("ICE has abducted a college athlete. What are the NCAA and universities going to do about it?"),
        )
        assertEquals(
            listOf("Should he worry?"),
            match("Republicans are spending millions to oust a Democrat who won by 187 votes. Should he worry?"),
        )

        assertTrue(match("What did the Chancellor announce in today's autumn statement?").isEmpty(),
            "A lone news question is not a trailing nudge")
        assertTrue(match("My husband never stops working, but he won't discuss it. Should I leave him?").isEmpty(),
            "A first-person advice question is not a debate nudge")
        assertTrue(match("Investors ask one question. Should hedge funds be worried?").isEmpty(),
            "'Should he' must not match the start of 'hedge'")
        assertTrue(match("Apple's new phone is out. Should you upgrade?").isEmpty(),
            "A review's 'Should you…?' is answered by the piece")
    }

    @Test
    fun `v7 insinuation needs 'quietly' to do something wrong`() {
        fun match(headline: String) = Signals.allMatches("insinuation", headline, emptySet())

        assertEquals(1, match("Bank quietly drops its climate pledge").size)
        assertTrue(match("Studio quietly released a patch on Friday").isEmpty(), "plain 'quietly' is manner, not insinuation")
    }

    @Test
    fun `broadsheet feature why matches lifestyle and demographic trend teasers`() {
        val teaser1 = "I turbocharged my career then pressed eject: why couples are getting divorced later"
        val m1 = Signals.allMatches("broadsheet_feature_why", teaser1, emptySet())
        assertEquals(1, m1.size)
        assertEquals("why couples are getting", m1.first().value)

        val teaser2 = "Why fans are digging their claws into Wolverine over its patronising gameplay"
        val m2 = Signals.allMatches("broadsheet_feature_why", teaser2, emptySet())
        assertEquals(1, m2.size)
        assertEquals("Why fans are digging", m2.first().value)

        val straight = "Why inflation remained unchanged during the last quarter"
        val mStraight = Signals.allMatches("broadsheet_feature_why", straight, emptySet())
        assertTrue(mStraight.isEmpty(), "Straight economic cause headline must not trigger lifestyle trend tease")
    }

    @Test
    fun `genre prefix separator and subtitle recognition with targeted muting`() {
        val review = "Star Wars Galactic Racer review: now this is frenetically fun podracing"
        val sepMatches = Signals.allMatches("genre_prefix_separator", review, defaultAllowlist)
        assertEquals(1, sepMatches.size)
        assertEquals("Star Wars Galactic Racer review: ", sepMatches.first().value)

        val subMatches = Signals.allMatches("genre_prefix_subtitle", review, defaultAllowlist)
        assertEquals(1, subMatches.size)
        assertEquals("now this is frenetically fun podracing", subMatches.first().value)

        // Deictic lead after genre prefix should be muted
        val deicticHeadline = "Pixel 9 Pro review - this is how to make an Android flagship"
        val deicticMatches = Signals.allMatches("cataphoric_deictic_lead", deicticHeadline, defaultAllowlist)
        assertTrue(deicticMatches.isEmpty(), "Deictic lead in review subtitle must be muted")

        // Without a genre prefix it is still a how-to, which is service phrasing
        // and not a tease (codebook rule 4).
        val rawDeictic = "This is how to make an Android flagship"
        for (signal in listOf("cataphoric_deictic_lead", "withheld_answer_here")) {
            assertTrue(Signals.allMatches(signal, rawDeictic, defaultAllowlist).isEmpty(), signal)
        }

        // Headlines without separator must not match genre_prefix_separator
        val plain = "UK to review defence spending after strategic assessment"
        val plainMatches = Signals.allMatches("genre_prefix_separator", plain, defaultAllowlist)
        assertTrue(plainMatches.isEmpty(), "Plain text using 'review' without separator must not match")
    }

    @Test
    fun `pseudo reassurance alarmism detects negated dread risks and keeps sober reporting clean`() {
        val plagueAlarm = "You Probably Aren't Going to Get the Plague"
        val mPlague = Signals.allMatches("pseudo_reassurance_alarm", plagueAlarm, defaultAllowlist)
        assertEquals(1, mPlague.size)

        val birdFlu = "Why you shouldn't panic about bird flu just yet"
        val mBirdFlu = Signals.allMatches("pseudo_reassurance_alarm", birdFlu, defaultAllowlist)
        assertEquals(1, mBirdFlu.size)

        val asteroid = "Relax, an asteroid probably won't wipe out Earth this week"
        val mAsteroid = Signals.allMatches("pseudo_reassurance_alarm", asteroid, defaultAllowlist)
        assertEquals(1, mAsteroid.size)

        val fleshEating = "No, you probably won't die from flesh-eating bacteria"
        val mFlesh = Signals.allMatches("pseudo_reassurance_alarm", fleshEating, defaultAllowlist)
        assertEquals(1, mFlesh.size)

        // Sober reporting controls must NOT match
        val sober1 = "Lab worker at plague institute in Siberia dies, prompting quarantine measures"
        val sober2 = "No plague cases detected among contacts of deceased lab worker, Russia says"
        val sober3 = "Russian lab worker's suspected plague death leaves experts puzzled"
        assertTrue(Signals.allMatches("pseudo_reassurance_alarm", sober1, defaultAllowlist).isEmpty())
        assertTrue(Signals.allMatches("pseudo_reassurance_alarm", sober2, defaultAllowlist).isEmpty())
        assertTrue(Signals.allMatches("pseudo_reassurance_alarm", sober3, defaultAllowlist).isEmpty())
    }

    @Test
    fun `temporal suspension threat captures looming catastrophe deferral`() {
        val grid = "The electric grid won't collapse this winter (for now)"
        val mGrid = Signals.allMatches("temporal_suspension_threat", grid, defaultAllowlist)
        assertEquals(1, mGrid.size)

        val savings = "Your savings aren't gone just yet"
        val mSavings = Signals.allMatches("temporal_suspension_threat", savings, defaultAllowlist)
        assertEquals(1, mSavings.size)

        val sober = "Officials confirm inflation dropped during the last quarter"
        assertTrue(Signals.allMatches("temporal_suspension_threat", sober, defaultAllowlist).isEmpty())
    }

    @Test
    fun `survival challenge framing detects existential staging of ordinary events`() {
        val heatwave = "Can your body survive the summer heatwave?"
        val mHeatwave = Signals.allMatches("survival_challenge_frame", heatwave, defaultAllowlist)
        assertEquals(1, mHeatwave.size)

        val budget = "Will your savings survive the autumn budget?"
        val mBudget = Signals.allMatches("survival_challenge_frame", budget, defaultAllowlist)
        assertEquals(1, mBudget.size)

        val viral = "Can You Survive 30 Seconds Looking At These Outfits"
        val mViral = Signals.allMatches("survival_challenge_frame", viral, defaultAllowlist)
        assertEquals(1, mViral.size)

        // Sober reporting of real survival
        val sober = "Two hikers survive blizzard in Scottish Highlands"
        assertTrue(Signals.allMatches("survival_challenge_frame", sober, defaultAllowlist).isEmpty())
    }

    @Test
    fun `prescriptive behavioral imperative detects withheld reasons and respects review muting`() {
        val phone = "Why you should never charge your phone overnight"
        val mPhone = Signals.allMatches("prescriptive_behavioral_imperative", phone, defaultAllowlist)
        assertEquals(1, mPhone.size)

        val food = "The 1 food you should never eat"
        val mFood = Signals.allMatches("prescriptive_behavioral_imperative", food, defaultAllowlist)
        assertEquals(1, mFood.size)

        // Subtitle muting after review prefix
        val review = "Pixel 9 Pro review: why you should rethink your choice of Android phone"
        val mReview = Signals.allMatches("prescriptive_behavioral_imperative", review, defaultAllowlist)
        assertTrue(mReview.isEmpty(), "Prescriptive lead in review subtitle should be muted")
    }

    @Test
    fun `cataphoric second person gap detects cognitive withholding and leaves declarative hedges clean`() {
        val facts = "39 Things You Probably Didn't Know About Hogwarts"
        val mFacts = Signals.allMatches("cataphoric_second_person_gap", facts, defaultAllowlist)
        assertEquals(1, mFacts.size)

        val detail = "The one detail you probably missed in the finale"
        val mDetail = Signals.allMatches("cataphoric_second_person_gap", detail, defaultAllowlist)
        assertEquals(1, mDetail.size)

        // Declarative reassurance (NOT a curiosity gap)
        val declarative = "You Probably Aren't Going to Get the Plague"
        val mDecl = Signals.allMatches("cataphoric_second_person_gap", declarative, defaultAllowlist)
        assertTrue(mDecl.isEmpty(), "Declarative statement must not trigger cataphoric curiosity gap")
    }
}



