package com.medialiteracy.flagging

import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These tests guard the decisions that are easy to undo by accident while
 * tweaking word lists. The headline test set in fixtures/ measures how good the
 * rules are; these tests check the rules still behave the way they're meant to.
 */
class HeadlineFlaggerTest {

    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    @Test
    fun `a single hedge word is never flagged`() {
        val result = flagger.flag("Rates could fall in the autumn")
        assertEquals(FlagLevel.NONE, result.level)
        assertTrue(result.hits.isEmpty(), "nothing should be reported below the quiet threshold")
    }

    @Test
    fun `inspect keeps the hits that flag hides, and agrees on level and score`() {
        val headline = "Rates could fall in the autumn"
        val inspected = flagger.inspect(headline)
        val flagged = flagger.flag(headline)
        assertEquals(FlagLevel.NONE, inspected.level)
        assertTrue(inspected.hits.isNotEmpty(), "inspect should keep the sub-threshold hedge")
        assertEquals(flagged.score, inspected.score)

        val loud = "Insiders say rebels plot revenge as war of words erupts"
        assertEquals(flagger.flag(loud).level, flagger.inspect(loud).level)
        assertEquals(flagger.flag(loud).hits, flagger.inspect(loud).hits)
    }

    @Test
    fun `hedges stack with other techniques`() {
        val result = flagger.flag("Sources say rates could fall in the autumn")
        assertEquals(FlagLevel.SUBTLE, result.level)
    }

    @Test
    fun `words used literally in ordinary news stay silent on their own`() {
        for (headline in listOf(
            "Bridge collapse kills three in northern Italy",
            "Met Office issues warning for heavy rain",
            "Deadly crash closes M6 in both directions",
        )) {
            assertEquals(FlagLevel.NONE, flagger.flag(headline).level, headline)
        }
    }

    @Test
    fun `science overhype needs a hedge and a claim and no stage qualifier`() {
        val hyped = flagger.flag("New battery could give EVs 1,000 miles per charge")
        assertEquals(FlagLevel.PROMINENT, hyped.level)
        assertTrue(Category.OVERHYPE in hyped.categories)
    }

    @Test
    fun `a headline that admits its own limits is never flagged as overhype`() {
        val honest = flagger.flag("New battery could double range in early-stage lab tests")
        assertTrue(Category.OVERHYPE !in honest.categories)
        assertEquals(FlagLevel.NONE, honest.level)
    }

    @Test
    fun `the same words are not counted by two categories at once`() {
        // "experts warn" is vague sourcing. The "warn" inside it must not also
        // be charged as manufactured fear.
        val result = flagger.flag("Experts warn the shortage may last months")
        assertEquals(FlagLevel.SUBTLE, result.level)
        assertTrue(Category.FEAR !in result.categories)
    }

    @Test
    fun `common acronyms are not treated as shouting`() {
        assertEquals(FlagLevel.NONE, flagger.flag("NASA and NATO agree joint review").level)
        assertEquals(FlagLevel.PROMINENT, flagger.flag("URGENT: act now").level)
    }

    @Test
    fun `whole words only`() {
        // "may" must not match inside "mayor", and "could" not inside "shoulder".
        assertEquals(FlagLevel.NONE, flagger.flag("Mayor opens new leisure centre").level)
    }

    @Test
    fun `turning a category off silences it`() {
        val noRage = HeadlineFlagger(
            RulePackLoader.loadDefault(),
            enabledCategories = Category.entries.toSet() - Category.RAGE,
        )
        assertEquals(FlagLevel.NONE, noRage.flag("Senator slams opponent in fiery exchange").level)
    }

    @Test
    fun `user muted headline is never flagged`() {
        val mutedHeadline = "Senator slams opponent in fiery exchange"
        val customFlagger = HeadlineFlagger(
            RulePackLoader.loadDefault(),
            mutedHeadlines = setOf(mutedHeadline),
        )
        assertEquals(FlagLevel.NONE, customFlagger.flag(mutedHeadline).level)
    }

    @Test
    fun `user muted phrase silences that trigger word`() {
        val customFlagger = HeadlineFlagger(
            RulePackLoader.loadDefault(),
            mutedPhrases = setOf("slams"),
        )
        assertEquals(FlagLevel.NONE, customFlagger.flag("Senator slams opponent in fiery exchange").level)
    }

    @Test
    fun `user muted outlet phrase silences only for that outlet`() {
        val customFlagger = HeadlineFlagger(
            RulePackLoader.loadDefault(),
            mutedOutletPhrases = mapOf("BBC" to setOf("slams")),
        )
        val headline = "Senator slams opponent in fiery exchange"
        assertEquals(FlagLevel.NONE, customFlagger.flag(headline, sourceName = "BBC").level)
        assertEquals(FlagLevel.SUBTLE, customFlagger.flag(headline, sourceName = "Sky News").level)
    }

    @Test
    fun `blank input is handled`() {
        assertEquals(FlagLevel.NONE, flagger.flag("").level)
        assertEquals(FlagLevel.NONE, flagger.flag("   ").level)
    }
}

/**
 * Behaviours added after running the rules against 482 real headlines. Each of
 * these is here because real copy did something the invented test set never did.
 */
class RealWorldPatternsTest {

    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    @Test
    fun `a possessive apostrophe is not a quote mark`() {
        // This matched from the apostrophe in "Edwards'" to the next quote and
        // reported the nonsense fragment "' plans for a '" as vague sourcing.
        val result = flagger.flag(
            "Huw Edwards' plans for a comeback as ex-friends break ranks over the presenter"
        )
        assertTrue(result.hits.none { it.matchedText.trim() in listOf("'", "' plans for a '") })
    }

    @Test
    fun `scare quotes are a soft signal, not a loud one`() {
        // A short quoted fragment buried in a long headline lets a paper print a
        // claim without asserting it. Real, but weak on its own.
        val result = flagger.flag(
            "Schools locked down in Edgware after man seen with 'gun' as armed police swarm area"
        )
        assertTrue(result.level != FlagLevel.PROMINENT)
    }

    @Test
    fun `words inside a quotation belong to the speaker, not the outlet`() {
        // Codebook rule 1. Before the 2026-10-10 fixture repair the first one
        // only stayed quiet because a corrupted quote made it a scare quote.
        val redman = flagger.flag(
            "Amanda Redman: 'My most embarrassing moment? Getting caught doing a pretend Bafta thank you speech'"
        )
        assertFalse(redman.isFlagged, "the actress's own word is not the outlet's opinion")

        val newsom = flagger.flag("California Gov. Newsom issues executive order to rein in AI 'before it's too late'")
        assertTrue(Category.FEAR !in newsom.categories, "the governor's quoted phrase is not the outlet's alarm")

        // The outlet's own words outside the quotation still count in full.
        val horror = flagger.flag("Horror as car ploughs into nursery playground 'like a bulldozer', witness says")
        assertTrue(Category.FEAR in horror.categories)
    }

    @Test
    fun `a headline that is mostly a quotation is not scare quotes`() {
        val result = flagger.flag("'We need to get it right': union leader on the coming tests")
        assertTrue(Category.SOURCING !in result.categories)
    }

    @Test
    fun `teaser openings are caught`() {
        assertTrue(
            Category.CURIOSITY in flagger.flag("Unmasked: The reality star behind the secret romance").categories,
        )
    }

    @Test
    fun `a moment that says what happens is a video description, not a tease`() {
        // Rule pack 24: nothing is held back, so the codebook's withheld
        // detail does not apply. "Moment" marks a video, like "Watch:".
        assertFalse(
            flagger.flag("Moment man stabs another twice before police dog drags him to the ground").isFlagged,
        )
    }

    @Test
    fun `a video label is not a teaser`() {
        // Broadcasters use these to say "this is a video", not to withhold.
        assertEquals(FlagLevel.NONE, flagger.flag("Watch: BBC crew catch thieves in the act").level)
        assertEquals(
            FlagLevel.NONE,
            flagger.flag("WATCH LIVE: Senate meets after advancing College Sports Act").level,
        )
    }

    @Test
    fun `hedging is not punished when the headline states its own limits`() {
        val honest = flagger.flag("Drug may slow tumour growth in mice, researchers say")
        assertEquals(FlagLevel.NONE, honest.level)
        assertTrue(Category.HEDGE !in honest.categories)
    }

    @Test
    fun `attribution to an institution counts for less than an anonymous source`() {
        val checkable = flagger.flag("Analysts say markets could face further turbulence")
        val anonymous = flagger.flag("Insiders say markets could face further turbulence")
        assertTrue(
            anonymous.score > checkable.score,
            "an unnamed insider should count for more than a named category of expert",
        )
    }

    @Test
    fun `a writer's name in capitals is not a warning`() {
        // The whole ALL-CAPS rule was deleted over this.
        assertEquals(
            FlagLevel.NONE,
            flagger.flag("SARAH VINE: Harry and Meghan's new life is already starting to unravel").level,
        )
    }
@Test
    fun `a writers verdict stated as fact is opinion, not reporting`() {
        val verdict = flagger.flag("Brendan Carr is more worried about who The View interviews than foreign owners")
        assertTrue(
            Category.OPINION in verdict.categories,
            "a comparative judgment about what someone cares about is the writer speaking",
        )
    }

    @Test
    fun `opinion alone never earns the loud mark`() {
        // The prominent mark represents high confidence, so a
        // single evaluative word must not reach it on its own. The quiet notice
        // is the right strength for "this is the writer's view, not an event".
        val single = flagger.flag("The rollout has been embarrassing for the department")
        assertTrue(
            single.level != FlagLevel.PROMINENT,
            "one opinion word should be a quiet notice at most, was ${single.level}",
        )
    }

    @Test
    fun `a judgment someone is named as making is reported, not the writers own`() {
        val quoted = flagger.flag("Rachel Reeves says the policy is a disaster for working families")
        assertTrue(
            Category.OPINION !in quoted.categories,
            "a named person's opinion being reported is not the headline writer's opinion",
        )
    }

    @Test
    fun `literal uses of violent words are not opinions`() {
        // These were left out of the rule on purpose; the test records why, so
        // nobody helpfully adds them back later.
        for (headline in listOf(
            "Police investigate brutal attack in city centre",
            "Perfect storm of rain and high tides floods coastal towns",
        )) {
            assertTrue(
                Category.OPINION !in flagger.flag(headline).categories,
                "\"$headline\" uses the word literally",
            )
        }
    }

    @Test
    fun `public safety and police appeals cancel urgency pressure`() {
        val missingAppeal = flagger.flag("Police issue urgent appeal after girl, 16, goes missing in Liverpool")
        assertTrue(
            Category.URGENCY !in missingAppeal.categories,
            "genuine police and missing person alerts must not be flagged as artificial urgency",
        )

        val policyUrgent = flagger.flag("House heads home to campaign amid calls for urgent AI action")
        assertTrue(
            Category.URGENCY !in policyUrgent.categories,
            "calls for legislative or policy action must not be flagged as artificial urgency",
        )
    }

    @Test
    fun `boxing matchup proper nouns like Fury-Joshua do not trigger fight talk`() {
        val boxingFixture = flagger.flag("Why Cardiff is set to win race to host Fury-Joshua")
        assertTrue(
            Category.RAGE !in boxingFixture.categories,
            "sports proper nouns like Fury-Joshua must be muted from fight talk",
        )
    }

    @Test
    fun `cricket scoring expressions like bashes maiden ton do not trigger conflict framing`() {
        val cricketReport = flagger.flag("England demolish Sri Lanka in ODI series decider as Banton bashes maiden ton")
        assertTrue(
            Category.RAGE !in cricketReport.categories,
            "sports scoring expressions like bashes maiden ton must not trigger conflict framing",
        )
        assertFalse(cricketReport.isFlagged, "cricket report should be completely unflagged")

        val politicalConflict = flagger.flag("Starmer bashes Tories over budget deficit")
        assertTrue(
            Category.RAGE in politicalConflict.categories,
            "political combat 'bashes Tories' must trigger conflict framing",
        )
    }

    @Test
    fun `broadsheet nudges still flag unless signed, and memorized v20 phrases stay gone`() {
        val nudge = flagger.flag("Tony Blair would like Andy Burnham to take his advice on rejoining the EU. Should he?")
        assertEquals(FlagLevel.SUBTLE, nudge.level)
        assertTrue(Category.OPINION in nudge.categories)

        val bribe = flagger.flag("Trump's $5,000 pledge to voters is a shameless bribe")
        assertTrue(Category.OPINION in bribe.categories)

        // Codebook rule 2: the byline has already told the reader whose view
        // it is, so the same words signed are not opinion presented as fact.
        for (signed in listOf(
            "Tony Blair would like Andy Burnham to take his advice on rejoining the EU. Should he? | Tom Baldwin",
            "Trump's $5,000 pledge to voters is a shameless bribe | Steven Greenhouse",
        )) {
            assertTrue(Category.OPINION !in flagger.flag(signed).categories, signed)
        }

        val featureWhy = flagger.flag("I turbocharged my career then pressed eject: why couples are getting divorced later")
        assertTrue(Category.CURIOSITY in featureWhy.categories)

        val memorised = listOf(
            "recipe for curtain-twitching disaster", "digital pathway to hell", "baddies now",
            "brought me to tears", "guts to stand up to", "delusional and dangerous", "delusional",
        )
        val allTerms = RulePackLoader.loadDefault().categories
            .flatMap { it.terms + it.noticeTerms + it.softTerms }
            .map { it.lowercase() }
            .toSet()
        assertTrue(memorised.none { it in allTerms }, "Phrases copied from single test headlines must stay out of the rule pack")
    }

    @Test
    fun `the two harvested headlines v20 wrongly flagged are left alone`() {
        assertFalse(flagger.flag("Everything about the forthcoming Tesla Roadster is utterly delusional").isFlagged)
        assertFalse(flagger.flag("No matter how deranged and delusional your podcast is, Liz Truss can go further | John Crace").isFlagged)
    }

    @Test
    fun `shouted capitals count as urgency, not opinion or withheld detail`() {
        // inspect: capitals too weak to flag alone are no longer shown beside the main technique (rule pack 35).
        val blade = flagger.inspect("Only in Florida! Terrifying moment crab armed with a BLADE swipes at homeowner and his dog")
        assertEquals(Category.URGENCY, blade.hits.first { it.matchedText == "BLADE" }.category)

        val more = flagger.inspect("Fed raises rates for first time in nearly three years... with Americans warned MORE hikes could be coming")
        assertEquals(Category.URGENCY, more.hits.first { it.matchedText == "MORE" }.category)
    }



    @Test
    fun `affiliate commerce sourcing and rush are caught under standard categories`() {
        val affiliateSourcing = flagger.flag("Dusk shoppers say they've found the 'perfect' hallway rug - and it's reduced to £21")
        assertTrue(
            Category.SOURCING in affiliateSourcing.categories,
            "shoppers say must be caught under unnamed sources",
        )

        val commercialRush = flagger.flag("Fans rush to buy viral autumn jacket before it sells out")
        assertTrue(
            Category.URGENCY in commercialRush.categories,
            "fans rush to buy must be caught under false rush",
        )
    }

    @Test
    fun `accusatory habit listicles and withheld science discoveries are caught`() {
        val accusatoryListicle = flagger.flag("5 Sunscreen Mistakes You're Probably Making, According to a Derm")
        assertTrue(
            Category.CURIOSITY in accusatoryListicle.categories,
            "accusatory habit listicles must be caught under curiosity gap",
        )

        val scienceDiscovery = flagger.flag("Einstein Called It \"Spooky.\" CERN Just Found It at Extreme Energies")
        assertTrue(
            Category.CURIOSITY in scienceDiscovery.categories,
            "withheld discovery formulas must be caught under curiosity gap",
        )
    }

    @Test
    fun `an announced answer is a tease even when it names the subject`() {
        // Codebook v3 rule 5: the headline sells an answer it has and won't give.
        for (headline in listOf(
            "Ben Affleck reveals why he cast his ex-wife as his love interest",
            "Pete Davidson Shares Why Becoming a Dad Made His Career Matter Less",
            "Actor shares the reason she quit Hollywood",
            "Revealed: Why only 100 England fans will be allowed to attend October's Nations League away clash at Croatia",
            "Scientists explain why cats purr",
            "Neuroscientist explains how his team grew human brain tissue in mice and breaks down what they found",
            "Finnish foreign ministry official details how Russia's 'hybrid warfare' reshaped border security",
        )) {
            val result = flagger.flag(headline)
            assertTrue(Category.CURIOSITY in result.categories, headline)
            assertEquals(FlagLevel.SUBTLE, result.level, headline)
        }

        // A plain question or topic, a how-to, or a headline that gives the answer.
        for (headline in listOf(
            "Why is the pound falling?",
            "Martin Lewis explains how to avoid the Ofgem energy price cap hike",
            "'Top Gun: Maverick' Star Glen Powell Explains Why He Doesn't Preach Politics: It Divides People",
        )) {
            assertTrue(Category.CURIOSITY !in flagger.flag(headline).categories, headline)
        }
    }

    @Test
    fun `somebody's words staged as a fight are conflict framing, an institution's response is not`() {
        // Codebook v3: a person responding to another person's words.
        for (headline in listOf(
            "Lily Allen responds to David Harbour's comments about her break-up album West End Girl",
            "Arteta hits back at Guardiola's jibe ahead of derby",
            "Star claps back at trolls after red carpet look",
        )) {
            assertTrue(Category.RAGE in flagger.flag(headline).categories, headline)
        }
        for (headline in listOf(
            "Police respond to shooting at Texas mall",
            "Government responds to criticism over NHS waiting lists",
            // A military strike is a real armed clash (codebook §4), not conflict framing.
            "Russia launches fresh attack on Kyiv as air raid sirens sound",
            "Israel launches fierce attack on Hezbollah positions",
        )) {
            assertTrue(Category.RAGE !in flagger.flag(headline).categories, headline)
        }
    }

    @Test
    fun `a loaded label in quotes with nobody behind it is anonymous sourcing`() {
        // Codebook v8 rule 1: a quoted label or sneer counts; quoted alleged conduct does not.
        for (headline in listOf(
            "Antisemitism 'endemic' in British universities as 16 staff-perpetrated incidents logged",
            "Council's 'woke' ban on Christmas lights leaves town square in darkness",
        )) {
            val result = flagger.flag(headline)
            assertTrue(Category.SOURCING in result.categories, headline)
            assertEquals(FlagLevel.SUBTLE, result.level, headline)
        }

        // A named or role-identified speaker, a mentioned word, a press review,
        // no claim, or alleged conduct (v8). The plain scare-quote signal may still count softly.
        for (headline in listOf(
            "Suspect foiled RAF Fairford 'bomb plot' by calling police before arrest",
            "Actress Selma Blair arrested after 'biting and punching crew' during flight",
            "Andy Burnham says there are 'strong indications' Iran is involved in RAF Fairford 'terror plot'",
            "Single mum who paid collapsed builder her life savings speaks out - as construction boss blames 'family tragedies' for the business going under",
            "Seattle's prosecutors are using a new alternative to the word 'gangs' to avoid causing offense",
            "The Papers: 'Giddy King' and 'war of words' over Earl Spencer book",
            "UK to enjoy up to 24C heat on Wednesday in 'unseasonable' September warm spell",
            // A quotation that opens the headline introduces the story, before a colon or a dash.
            "'Bomb plot': police arrest two men at airport after tip-off from cleaning staff",
            "'Bomb plot' - police arrest two men at airport after tip-off from cleaning staff",
            "'Bomb plot' — police arrest two men at airport after tip-off from cleaning staff",
        )) {
            assertTrue(Signals.allMatches("dramatic_scare_quote", headline, emptySet()).isEmpty(), headline)
        }

        // An opening quotation that runs straight into the sentence is still a scare quote.
        val runOn = "'Sham inquiry' chair held talks with minister before appointment"
        assertTrue(Signals.allMatches("dramatic_scare_quote", runOn, emptySet()).isNotEmpty(), runOn)
    }

    @Test
    fun `a fear word counts when it stands in for the facts, not next to a stated harm`() {
        // Codebook v8 rule 3 (spot-check): the threat is vague or still to come.
        for (headline in listOf(
            "Horror as family return home to find their house transformed",
            "Nightmare winter ahead for millions of households",
        )) {
            assertTrue(Category.FEAR in flagger.flag(headline).categories, headline)
        }
        for (headline in listOf(
            // A fear word next to a harm the headline states is description (v8).
            "Horrifying moment girl, 14, hit by car on live TV",
            "Two dead after horror crash between camper van and lorry in Somerset village",
            "Harrowing ordeal of family trapped in flood",
            "Footage shows jet nosediving after takeoff",
            "Huge fundraiser launched after heartbreaking death of 3-year-old Noah Woods",
            "Control Resonant review: A violent, twisted horror of a video game packed with action",
            "Moment police tackle knifeman in Oxford Street",
        )) {
            assertTrue(Category.FEAR !in flagger.flag(headline).categories, headline)
        }
    }

    @Test
    fun `a statement the headline holds back is a tease`() {
        // Codebook v2: "breaks silence", "speaks out" and "reacts to" without what was said.
        for (headline in listOf(
            "Lindsay Clancy's Husband and Holdout Juror Break Their Silence",
            "Mauricio Umansky Reacts to Stepdaughter Farrah Aldjufrie's Pregnancy",
            "Confused Coronation Street fans speak out on David Platt change as actor teases dramatic future",
            "Louis Page breaks silence on Man United transfer after failed bid and says 'I believe'",
        )) {
            assertTrue(Category.CURIOSITY in flagger.flag(headline).categories, headline)
        }

        // The headline says what was said, or it is not one statement at all.
        for (headline in listOf(
            "Katie Price breaks her silence on Lee Andrews split and says 'he scammed my heart'",
            "Lindsay Clancy Case Holdout Juror Breaks Silence, Says 'Activists' Wanted 'Not Guilty' on Day 1",
            "'Difficult to say with a straight face': Tapper reacts to Fox News' statement on settlement",
            "MP speaks out against assisted dying bill",
            "Survivors who speak out face backlash",
            "Stick-on Logitech sensor lets offices react to the people inside them",
            "Stocks react to jobs report",
            "Margaret Busby: 'My friend Bonnie Greer fearlessly spoke out and forged connections'",
        )) {
            assertTrue(Category.CURIOSITY !in flagger.flag(headline).categories, headline)
        }
    }

    @Test
    fun `this or these pointing at something never named is withheld detail`() {
        // The codebook's own definition of a curiosity gap.
        for (headline in listOf(
            "This semiconductor stock is in the dumps. BMO says it can bounce back",
            "If you don't like using Siri, this iOS 27 feature may change your mind",
            "Mount Vesuvius Buried These Scrolls for 2,000 Years – Now Scientists May Read Them",
            "A cup of coffee will cost you \$8 in this city",
        )) {
            assertTrue(Category.CURIOSITY in flagger.flag(headline).categories, headline)
        }

        // The underline marks the unnamed thing, not the claim about it.
        val stock = flagger.flag("This semiconductor stock is in the dumps. BMO says it can bounce back")
        assertEquals("This semiconductor stock", stock.hits.first { it.category == Category.CURIOSITY }.matchedText)
    }

    @Test
    fun `this and these that point at time, titles, lists or the writer's own argument are left alone`() {
        for (headline in listOf(
            "Ministers to set out housing plans this week",
            "Why scientists flew into firestorm clouds this wildfire season",
            "These are the first five Googlebook laptops",
            "Cheryl belts out Fight For This Love on a night out in Manchester",
            "Flatter your heart-shaped face with these stylist-approved tips",
            "Save more than \$100 off this popular Dyson vacuum right now",
            "Workers say: 'I can't deal with this stress anymore'",
            "QUENTIN LETTS: there were moments in this speech when sugar levels were so high",
        )) {
            assertTrue(Category.CURIOSITY !in flagger.flag(headline).categories, headline)
        }
    }

    @Test
    fun `an explainer that names its subject is not a curiosity gap`() {
        // Codebook rule 5: a question the article answers is not automatically
        // a tease. The why-phrase patterns that flagged these were removed in
        // rule pack 24; several had been copied from single harvested headlines.
        assertEquals(FlagLevel.NONE, flagger.flag("Why Cardiff is set to win race to host Fury-Joshua").level)
    }

    @Test
    fun `financial forward-referencing is flagged under speculation`() {
        val stockCataphora = flagger.flag("Tokenization is set to change stock trading. These stocks could get a boost")
        assertTrue(
            Category.HEDGE in stockCataphora.categories,
            "financial cataphora ('these stocks could get a boost') must be categorized under speculation",
        )
        assertEquals(FlagLevel.SUBTLE, stockCataphora.level)
    }

    @Test
    fun `civic and institutional explainers are cancelled`() {
        val unExplainer = flagger.flag("United Nations says US may have committed war crimes in Iran: What it means")
        assertTrue(
            Category.CURIOSITY !in unExplainer.categories,
            "institutional explainers with United Nations must be cancelled",
        )

        val railwayPolicy = flagger.flag("Sixth train operator - Chiltern Railways - to go into public ownership in days - what it means for you")
        assertTrue(
            Category.CURIOSITY !in railwayPolicy.categories,
            "public ownership announcements must be cancelled",
        )
    }

    @Test
    fun `here's and this is announce an answer, but service phrasing is left alone`() {
        // Codebook v3 rule 5: an announced answer is withheld detail even when
        // the subject is named.
        for (withheld in listOf(
            "Here's how much it actually costs to be a bridesmaid",
            "Here's what happens when you swallow your gum",
            "I asked 5 fascism experts whether Donald Trump is a fascist. Here's what they said.",
            "We bought our £242,000 home without a deposit - here's how",
            "Here's why Apple's Mac Studio has become so expensive",
            "More missing women linked to \"house of horrors\" in Philadelphia. Here's what we know.",
            "This is why your phone gets hot",
        )) {
            assertTrue(Category.CURIOSITY in flagger.flag(withheld).categories, withheld)
        }

        // Codebook rule 4: service phrasing promises instructions, not a payoff.
        for (service in listOf(
            "Ofgem energy price cap hike to hit UK from Thursday. Here's what you need to do",
            "Here's what to know about the new tax rules",
            "Here's how to watch the eclipse tonight",
            "Here's what to do if your pipes freeze",
            "Here's what you need to know if you haven't filed your return yet",
            "Social anxiety is more common than many think. Here's what you need to know about the condition.",
        )) {
            assertEquals(FlagLevel.NONE, flagger.flag(service).level, service)
        }

        // Codebook v5, rule 5: a deficit hook tacked onto a news event, and a
        // reader's dilemma closed by a verdict question, hold back the answer.
        for (tease in listOf(
            "Cardinal football is back - here's what you need to know",
            "Cardinal football is back – here’s what you need to know",
            "My financial adviser wants me to invest $350k in UAE data center. Is this risky?",
            // Rule pack 29: the hook closing a news headline, whoever it addresses.
            "AI stock swings could affect your 401(k). Here's what retirement savers need to know",
            // Rule pack 29: any yes/no question put to the columnist holds back the verdict.
            "My husband never stops working, but he won't discuss it. Should I leave him?",
        )) {
            assertTrue(Category.CURIOSITY in flagger.flag(tease).categories, tease)
        }
        for (explainer in listOf(
            "Covid jab for under-5s: is it safe?",
            "US rates are rising. Is this risky?",
        )) {
            assertTrue(Signals.allMatches("advice_dilemma_cliffhanger", explainer, emptySet()).isEmpty(), explainer)
        }
    }

    @Test
    fun `trailing rhetorical question survives dash normalisation and never underlines the byline`() {
        val xi = flagger.flag("Trump swoons over strongman soulmate Xi – are you feeling jealous, Vladimir?")
        assertTrue(Category.OPINION in xi.categories, "An en-dash separated nudge must still be caught after dashes are normalised")

        val harris = flagger.flag("Kamala Harris is belatedly courting progressives. Is it too little, too late?")
        val hit = harris.hits.first { it.category == Category.OPINION }
        assertEquals("Is it too little, too late?", hit.matchedText)

        // The signal itself must still stop at the question when a byline
        // follows, even though codebook rule 2 keeps a signed column from
        // being charged with opinion as fact.
        val signed = "Kamala Harris is belatedly courting progressives. Is it too little, too late? | Arwa Mahdawi"
        val match = Signals.allMatches("trailing_rhetorical_question", signed, emptySet()).single()
        assertEquals("Is it too little, too late?", match.value)
        assertFalse(match.value.contains("Mahdawi"), "The underline must never cover the writer's name")
        assertTrue(Category.OPINION !in flagger.flag(signed).categories)
    }

    @Test
    fun `inflected single words match morphological variants without explicit rulepack entries`() {
        val plunged = flagger.flag("Markets plunged as fears grow across Europe")
        assertTrue(
            Category.FEAR in plunged.categories,
            "fear category should catch 'plunged' from 'plunge'/'fears grow'",
        )
    }

    @Test
    fun `zero width spaces and non breaking spaces are normalized 1 to 1 without breaking match spans`() {
        val zwspHeadline = "Sources\u200B say the minister will resign"
        val result = flagger.flag(zwspHeadline)
        assertEquals(FlagLevel.SUBTLE, result.level)
        assertTrue(Category.SOURCING in result.categories)
        val hit = result.hits.first { it.category == Category.SOURCING }
        assertEquals(0..11, hit.range)
        assertEquals(zwspHeadline.substring(0..11), hit.matchedText)

        val nbspHeadline = "Sources\u00A0say the minister will resign"
        val nbspResult = flagger.flag(nbspHeadline)
        assertEquals(FlagLevel.SUBTLE, nbspResult.level)
        assertTrue(Category.SOURCING in nbspResult.categories)
    }

    @Test
    fun `cyrillic homoglyphs are folded into latin characters without evading detection`() {
        // Cyrillic 'о' (\u043E) in "wоn't"
        val evasive = flagger.flag("You w\u043En't believe what happened next")
        assertEquals(FlagLevel.PROMINENT, evasive.level)
        assertTrue(Category.CURIOSITY in evasive.categories)
        val hit = evasive.hits.first { it.category == Category.CURIOSITY }
        assertEquals("You w\u043En't believe", hit.matchedText)
    }

    @Test
    fun `trailing author bylines after colon or pipe do not trigger urgency caps`() {
        val dershowitz = flagger.flag("The holdout juror must be protected: ALAN DERSHOWITZ")
        assertFalse(
            Category.URGENCY in dershowitz.categories,
            "Author byline ': ALAN DERSHOWITZ' must not trigger manufactured urgency",
        )

        val callahan = flagger.flag("Harry and Meghan face another controversy: MAUREEN CALLAHAN")
        assertFalse(
            Category.URGENCY in callahan.categories,
            "Author byline ': MAUREEN CALLAHAN' must not trigger manufactured urgency",
        )

        val wilson = flagger.flag("A sobering tactical assessment of the defeat | JONATHAN WILSON")
        assertFalse(
            Category.URGENCY in wilson.categories,
            "Author byline '| JONATHAN WILSON' must not trigger manufactured urgency",
        )
    }

    @Test
    fun `trailing clickbait teasers with colon are not mistaken for author bylines`() {
        val matches = Signals.allMatches(
            "mid_headline_caps",
            "President signs emergency order: WHY THIS MATTERS",
            emptySet(),
        )
        assertTrue(
            matches.any { it.value == "WHY" || it.value == "THIS" },
            "Teaser ': WHY THIS MATTERS' should match mid_headline_caps and not be muted as an author byline",
        )
    }

    @Test
    fun `conflict and sourcing words count in every form`() {
        listOf(
            // "slamming" was retired in rule pack 31 (no tuning support under the v8 labels).
            "Family hit out at police over decision to drop murder inquiry",
            "Fans blast striker for diving in cup defeat",
        ).forEach { headline ->
            assertTrue(Category.RAGE in flagger.flag(headline).categories, "Expected conflict framing: $headline")
        }
        listOf(
            "Pop star's new romance faces a common relationship trap, expert warns",
            "Former party insider reveals plan to oust leader",
            "Bank chiefs to attend state dinner at the White House: Sources",
            "Carmaker to cut 2,000 jobs -sources",
        ).forEach { headline ->
            assertTrue(Category.SOURCING in flagger.flag(headline).categories, "Expected anonymous sourcing: $headline")
        }
    }

    @Test
    fun `a word used in another sense is not conflict or alarm`() {
        listOf(
            "Storm Amy slams Scotland with 90mph winds",
            "Lorry slams into railway bridge",
            "The blasted cat despises me and my wife adores him",
            "Rebels expand strikes to Saudi Arabia, alarming the U.S.",
            "Tech giant open-sources its new model",
        ).forEach { headline ->
            assertEquals(FlagLevel.NONE, flagger.flag(headline).level, headline)
        }
    }

    @Test
    fun `literal disaster strike shutdown and party bashes do not flag as manufactured rage`() {
        // Natural disaster damage
        val hurricane = flagger.flag("Hurricane destroys hundreds of homes in Florida")
        assertFalse(
            Category.RAGE in hurricane.categories,
            "Natural disaster damage must not flag as manufactured rage: ${hurricane.categories}",
        )
        assertEquals(FlagLevel.NONE, hurricane.level)

        // Labor strike
        val strike = flagger.flag("Workers strike at Amazon over pay and working hours")
        assertFalse(
            Category.RAGE in strike.categories,
            "Labor strike must not flag as manufactured rage: ${strike.categories}",
        )
        assertEquals(FlagLevel.NONE, strike.level)

        // Industrial plant shutdown
        val plant = flagger.flag("Ford shuts down Michigan plant amid supply shortage")
        assertFalse(
            Category.RAGE in plant.categories,
            "Industrial facility shutdown must not flag as manufactured rage: ${plant.categories}",
        )
        assertEquals(FlagLevel.NONE, plant.level)

        // Social celebration bash
        val birthday = flagger.flag("Royal family attends star-studded birthday bash in London")
        assertFalse(
            Category.RAGE in birthday.categories,
            "Birthday bash must not flag as manufactured rage: ${birthday.categories}",
        )
        assertEquals(FlagLevel.NONE, birthday.level)

        // Sensational / interpersonal conflict MUST still flag
        val polShutdown = flagger.flag("Senator shuts down critic in heated exchange")
        assertTrue(
            Category.RAGE in polShutdown.categories,
            "Rhetorical shutdown must flag as manufactured rage",
        )
        assertEquals(FlagLevel.SUBTLE, polShutdown.level)

        val celebDestroys = flagger.flag("Celebrity destroys rival in podcast")
        assertTrue(
            Category.RAGE in celebDestroys.categories,
            "Interpersonal destruction must flag as manufactured rage",
        )
        assertEquals(FlagLevel.PROMINENT, celebDestroys.level)
    }

    @Test
    fun `prefixed and embedded explainers that name their subject are left alone`() {
        // These were all flagged before rule pack 24, most by why-phrase
        // fragments copied from these very headlines. Codebook rule 5 and the
        // 2026-10-10 relabel make them explainers.
        val explainers = listOf(
            "Fatal October flaws: Why each MLB contender's biggest weakness will -- or won't -- sink it",
            "Making too much dough? Why Bread Ahead’s success at London’s Borough Market now has traders worried",
            "‘Just cut the wires’: why UK conspiracy theorists are destroying traffic scanners",
            "Bred for success - why Backstedt can hit cycling's peaks",
            "MobLand season two review – why is Pierce Brosnan’s tedious crime drama so obsessed with testicle jokes?",
            "Why mortgage bonds are set to deteriorate, and possibly hit the whole market",
            "Why Norwich boss claims Man City players got annoyed",
            "Why US plan to sell F-35 warplanes to Saudi Arabia is controversial",
            "Why is there a waitlist for Siri AI in iOS 27?",
        )
        for (headline in explainers) {
            assertTrue(
                Category.CURIOSITY !in flagger.flag(headline).categories,
                "An explainer naming its subject is not withheld detail: '$headline'",
            )
        }

        // Holding back the key fact itself is still withheld detail.
        val withheld = flagger.flag("Scientists Discover a Key Reason Why the Aging Eye Struggles To Regrow Lost Neurons")
        assertTrue(Category.CURIOSITY in withheld.categories)
    }

    @Test
    fun `generalized curiosity overhype and opinion patterns flag correctly`() {
        // Cataphoric threshold
        val thresh = flagger.flag("Stocks could tumble if yields reach this level")
        assertTrue(Category.CURIOSITY in thresh.categories, "Expected CURIOSITY for cataphoric threshold")

        // Imperative reaction hook
        val react = flagger.flag("Hear panelist's reaction to the breaking news")
        assertTrue(Category.CURIOSITY in react.categories, "Expected CURIOSITY for imperative reaction hook")

        // Cataphoric lead in secondary sentence. "This is exactly how to stop it"
        // would be a how-to (codebook rule 4); an announced reason is a tease.
        val lead = flagger.flag("A strange condition is affecting millions. This is exactly why")
        assertTrue(Category.CURIOSITY in lead.categories, "Expected CURIOSITY for sentence-initial cataphoric lead")

        // Generalized cliffhanger
        val cliff = flagger.flag("The weather system is about to do something unexpected")
        assertTrue(Category.CURIOSITY in cliff.categories, "Expected CURIOSITY for generalized cliffhanger")

        // Generalized science overhype
        val overhype = flagger.flag("Giant tortoise holds the key to humans living until 150")
        assertTrue(Category.OVERHYPE in overhype.categories, "Expected OVERHYPE for holds the key to")

        // Generalized editorial hyperbole
        val opinion = flagger.flag("Startup targets world dominance with new search tool")
        assertTrue(Category.OPINION in opinion.categories, "Expected OPINION for world dominance")
    }

    @Test
    fun `a signed column is never opinion as fact but can still tease`() {
        // Codebook rule 2 exempts signed opinion from opinion_as_fact only;
        // "This is what I'm hearing" is an announced answer (codebook v3 rule 5).
        val result = flagger.flag(
            "Meghan's plot to become the Queen of the Cotswolds: WhatsApp is abuzz and rumours are flying. This is what I'm hearing about the duchess's big plans for the future - but this is the woman who will stand in her way, reveals NADINE DORRIES"
        )
        assertTrue(Category.OPINION !in result.categories)
        assertTrue(Category.CURIOSITY in result.categories)
    }

    @Test
    fun `conversational deictic phrasing like now this is podracing is clean even with feed summary`() {
        val headline = "Star Wars Galactic Racer review: now this is frenetically fun podracing with a totally unforgiving streak"
        val summary = "Becoming a boost junkie in arcade racers that offer such potent bursts of extra oomph is usually only a matter of time. Star Wars: Galactic Racer is no different. The hooks have been in deep."
        val result = flagger.flagWithSummary(headline, summary)
        assertEquals(FlagLevel.NONE, result.level, "Review quote with 'now this is' should not be flagged as clickbait or curiosity gap")
    }

    @Test
    fun `genre and section prefixes mute conversational deictic and subtitle commentary but preserve blatant clickbait`() {
        // Deictic subtitle after dash separator in review
        val deicticReview = flagger.flag("Star Wars Outlaws review - this is how to make an open world")
        assertEquals(FlagLevel.NONE, deicticReview.level, "Review with deictic subtitle clause should be clean")

        // Codebook rule 2: a labeled review or column has told the reader it
        // is opinion, so its verdict is not opinion presented as fact.
        val opinionReview = flagger.flag("Elden Ring review: a visual masterclass that nails the open world")
        assertEquals(FlagLevel.NONE, opinionReview.level, "A labeled review's verdict is not opinion as fact")

        val columnCommentary = flagger.flag("Column: the prime minister's speech was tone-deaf and laughable")
        assertEquals(FlagLevel.NONE, columnCommentary.level, "A labeled column's verdict is not opinion as fact")

        // The same verdict with no label is the outlet speaking.
        val unlabelled = flagger.flag("The prime minister's speech was tone-deaf and laughable")
        assertTrue(Category.OPINION in unlabelled.categories)

        // User example from Rock Paper Shotgun
        val reviewEllipsis = flagger.flag("Star Wars Glactic Racer review: now this is frenetically fun podracing...")
        assertEquals(FlagLevel.NONE, reviewEllipsis.level, "Review with conversational deictic subtitle should be clean")

        // Blatant clickbait preserved
        val preservedCuriosity = flagger.flag("Game review: You won't believe what happens at the end")
        assertTrue(
            Category.CURIOSITY in preservedCuriosity.categories,
            "Blatant curiosity gap in review must remain flagged",
        )

        // Investigative and Local news
        val investigative = flagger.flag("Investigation: How local officials are hiding the truth")
        assertEquals(FlagLevel.NONE, investigative.level, "Investigative piece with subtitle clause should be clean")
        
        // A section label explains what kind of piece it is; it never excuses a
        // tease. "This is what residents think" holds back what they think.
        val localNews = flagger.flag("Local Report: This is what residents think of the new bypass")
        assertTrue(Category.CURIOSITY in localNews.categories, "A genre label does not excuse an announced answer")

        val opEd = flagger.flag("Op-Ed: The mayor's plan is an absolute disaster")
        assertEquals(FlagLevel.NONE, opEd.level, "A labeled op-ed's verdict is not opinion as fact (codebook rule 2)")
        assertTrue(Category.OPINION in flagger.flag("The mayor's plan is an absolute disaster").categories)
    }

    @Test
    fun `pseudo reassurance alarmism flags negated dread risks as manufactured fear`() {
        val plague = flagger.flag("You Probably Aren't Going to Get the Plague")
        assertEquals(FlagLevel.PROMINENT, plague.level)
        assertTrue(Category.FEAR in plague.categories, "Plague pseudo-reassurance must be flagged as FEAR")
        assertFalse(Category.CURIOSITY in plague.categories, "Plague headline must not be miscategorized as curiosity")

        val birdFlu = flagger.flag("Why you shouldn't panic about bird flu just yet")
        assertEquals(FlagLevel.PROMINENT, birdFlu.level)
        assertTrue(Category.FEAR in birdFlu.categories)

        val fleshEating = flagger.flag("No, you probably won't die from flesh-eating bacteria")
        assertEquals(FlagLevel.PROMINENT, fleshEating.level)
        assertTrue(Category.FEAR in fleshEating.categories)

        val asteroid = flagger.flag("Relax, an asteroid probably won't wipe out Earth this week")
        assertEquals(FlagLevel.PROMINENT, asteroid.level)
        assertTrue(Category.FEAR in asteroid.categories)
    }

    @Test
    fun `temporal threat suspension flags looming catastrophe deferral`() {
        val grid = flagger.flag("The electric grid won't collapse this winter (for now)")
        assertEquals(FlagLevel.SUBTLE, grid.level)
        assertTrue(Category.FEAR in grid.categories)
    }

    @Test
    fun `survival challenge framing flags existential staging of seasonal and economic events`() {
        val heatwave = flagger.flag("Can your body survive the summer heatwave?")
        assertEquals(FlagLevel.SUBTLE, heatwave.level)
        assertTrue(Category.FEAR in heatwave.categories)

        val budget = flagger.flag("Will your savings survive the autumn budget?")
        assertEquals(FlagLevel.SUBTLE, budget.level)
        assertTrue(Category.FEAR in budget.categories)
    }

    @Test
    fun `prescriptive behavioral imperatives flag withheld reason habits`() {
        val phone = flagger.flag("Why you should never charge your phone overnight")
        assertEquals(FlagLevel.SUBTLE, phone.level)
        assertTrue(Category.CURIOSITY in phone.categories)

        // Muted in reviews
        val review = flagger.flag("Pixel 9 Pro review: why you should rethink your choice of Android phone")
        assertEquals(FlagLevel.NONE, review.level)
    }

    @Test
    fun `sober disease and event coverage remains clean`() {
        val sober1 = flagger.flag("Lab worker at plague institute in Siberia dies, prompting quarantine measures")
        assertEquals(FlagLevel.NONE, sober1.level)

        val sober2 = flagger.flag("No plague cases detected among contacts of deceased lab worker, Russia says")
        assertEquals(FlagLevel.NONE, sober2.level)

        val sober3 = flagger.flag("Two hikers survive blizzard in Scottish Highlands")
        assertEquals(FlagLevel.NONE, sober3.level)
    }

    @Test
    fun `every hit names the rule that fired and what it added`() {
        // The match receipt: an inspection view can name the rule, and a
        // "did we flag this correctly?" answer can point straight at it.
        val exchange = flagger.flag("Lily Allen responds to David Harbour's comments about her album")
        val rage = exchange.hits.single { it.category == Category.RAGE }
        assertEquals("signal:staged_exchange", rage.ruleId)
        assertEquals(0.5, rage.weight)

        val slams = flagger.flag("Minister slams rival over housing plan")
        val term = slams.hits.single { it.category == Category.RAGE }
        assertEquals("term:slams", term.ruleId)
        assertEquals(term.weight, slams.score)
    }

    @Test
    fun `an article the feed calls opinion is never opinion as fact but can still tease`() {
        // Codebook rule 2 from the article's own type, not only from a byline in the headline.
        val thesis = "The prime minister's speech was tone-deaf and laughable"
        assertTrue(Category.OPINION in flagger.flag(thesis).categories, "Unlabeled, it is the outlet speaking")
        for (type in listOf(ArticleType.OPINION, ArticleType.EDITORIAL, ArticleType.REVIEW, ArticleType.LETTERS)) {
            assertTrue(Category.OPINION !in flagger.flag(thesis, articleType = type).categories, type.name)
        }
        assertTrue(Category.OPINION in flagger.flag(thesis, articleType = ArticleType.NEWS).categories)
        assertTrue(Category.OPINION in flagger.flag(thesis, articleType = ArticleType.ANALYSIS).categories)

        // A column can still tease, and "this" in a signed view points at the writer's argument.
        val tease = flagger.flag("Here's why the pound is falling", articleType = ArticleType.OPINION)
        assertTrue(Category.CURIOSITY in tease.categories)
        assertTrue(Signals.allMatches("unnamed_demonstrative", "This speech was a turning point", emptySet(), ArticleType.OPINION).isEmpty())
    }

    @Test
    fun `declarative statement of not missing a feature is not urgency`() {
        val result = flagger.flag("I don't miss the camera on the Ray-Ban Meta Audio glasses", articleType = ArticleType.REVIEW)
        assertEquals(FlagLevel.NONE, result.level)
        assertTrue(Category.URGENCY !in result.categories)
    }

    @Test
    fun `a study the article names is not study as proof`() {
        // Codebook v12, from the 2026-10 flag review; its own technique since codebook v13.
        val headline = "One-Quarter of Data Centers Are Located in Water-Stressed Areas, Study Shows"
        val bare = flagger.flag(headline, ArticleContext())
        assertTrue(Category.STUDY in bare.categories)
        assertTrue(Category.SOURCING !in bare.categories)
        val named = ArticleContext(storyText = "The study, led by a team from the Carnegie Institution for Science, mapped 9,347 data centers.")
        val result = flagger.flag(headline, named)
        assertTrue(Category.STUDY !in result.categories)
        assertTrue(Category.SOURCING !in result.categories)
    }

    @Test
    fun `study as proof asks what else the study found`() {
        val hit = flagger.flag("Women's autoimmune symptoms more likely to be dismissed as psychological, study finds")
            .hits.first { it.category == Category.STUDY }
        assertTrue(hit.question!!.contains("what else it found"))
        val poll = flagger.flag("Yet another poll shows Reform UK falling to third place behind Tories")
            .hits.first { it.category == Category.STUDY }
        assertTrue(poll.question!!.contains("just the number that fits"))
    }
}
