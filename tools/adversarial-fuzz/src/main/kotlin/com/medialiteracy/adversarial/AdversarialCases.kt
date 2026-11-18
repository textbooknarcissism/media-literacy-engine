package com.medialiteracy.adversarial

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel

/**
 * An adversarial challenge case for testing HeadlineFlagger.
 */
data class AdversarialCase(
    val headline: String,
    val expectedLevel: FlagLevel,
    val targetCategory: Category?,
    val challengeType: ChallengeType,
    val rationale: String,
)

enum class ChallengeType {
    /** A legitimate, straight headline containing words that might trap naive filters. Must NOT be flagged. */
    FALSE_POSITIVE_TRAP,

    /** A manipulative headline attempting to evade detection using synonyms or indirect syntax. MUST be caught. */
    EVASION_ATTACK,
}

internal object AdversarialBattery {

    val BUILT_IN_CASES: List<AdversarialCase> = listOf(
        // ==========================================
        // 1. FALSE POSITIVE TRAPS (Should be NONE)
        // ==========================================

        // Sports fixtures with competitive combat verbs
        AdversarialCase(
            headline = "Arsenal defeat Chelsea 2-1 in tense London derby",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Legitimate sports fixture report using athletic verbs; not political fight talk."
        ),
        AdversarialCase(
            headline = "Warriors strike back in third quarter to beat Lakers",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Sports reporting describing basketball comeback."
        ),
        AdversarialCase(
            headline = "Chiefs crush 49ers 25-22 in overtime to secure Super Bowl title",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Legitimate athletic championship recap using common sports victory metaphor."
        ),
        AdversarialCase(
            headline = "Real Madrid destroy Girona 4-0 to extend league lead",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Ordinary European football scoreline report."
        ),

        // Literal physical disasters and official alerts
        AdversarialCase(
            headline = "Twelve dead and thirty injured as bridge collapses in northern Italy",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Literal casualty and disaster reporting; tragic facts must not be flagged as scare wording."
        ),
        AdversarialCase(
            headline = "National Weather Service issues tornado warning for central Oklahoma",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Official meteorological security alert with verifiable geographical scope."
        ),
        AdversarialCase(
            headline = "Three firefighters injured in two-alarm warehouse fire",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Straight accident report with concrete numbers and roles."
        ),
        AdversarialCase(
            headline = "Category 4 Hurricane Helene makes landfall in Florida with 140 mph winds",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Severe weather reporting with meteorological classification and wind speed measurements."
        ),
        AdversarialCase(
            headline = "Amtrak passenger train derails in Montana leaving three dead and fifty injured",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Straightforward factual transit accident reporting with casualty counts."
        ),
        AdversarialCase(
            headline = "Earthquake of magnitude 7.1 strikes southern Japan, triggering tsunami advisory",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Official seismic measurement and agency advisory notification."
        ),

        // Normal economic / meteorological hedging
        AdversarialCase(
            headline = "Scattered showers may develop across the Midlands by evening",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.HEDGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Ordinary weather forecast with epistemic modal; lone modal in forecast is not clickbait."
        ),
        AdversarialCase(
            headline = "Central bank notes interest rates could remain unchanged if inflation slows",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.HEDGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Conditioned economic policy outlook with named institutional attribution."
        ),
        AdversarialCase(
            headline = "Federal Reserve leaves benchmark interest rate unchanged at 5.25 to 5.5 percent",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.HEDGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Factual monetary policy decision with exact interest rate targets."
        ),
        AdversarialCase(
            headline = "Oil prices drop 3 percent after OPEC announces plans to increase crude production",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Checkable commodity market movement linked to official organization statement."
        ),
        AdversarialCase(
            headline = "Treasury yields slide following weaker-than-expected monthly jobs report",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Routine government economic indicator and bond market reaction."
        ),

        // Named journalism attribution
        AdversarialCase(
            headline = "Health Secretary Wes Streeting warns NHS waiting lists will take years to clear",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.SOURCING,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Named cabinet minister providing checkable public statement."
        ),
        AdversarialCase(
            headline = "Federal Reserve Chair Jerome Powell says rate cuts depend on labor data",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.SOURCING,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Checkable official attribution; not an anonymous insider."
        ),
        AdversarialCase(
            headline = "Secretary of State Antony Blinken arrives in Cairo for Gaza ceasefire negotiations",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.SOURCING,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Public diplomatic travel by named cabinet official on official mission."
        ),
        AdversarialCase(
            headline = "NATO allies pledge 40 billion euros in security assistance for Ukraine",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.SOURCING,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Multilateral alliance summit commitment with concrete financial figure."
        ),

        // Legal proceedings and court rulings
        AdversarialCase(
            headline = "Supreme Court rules 6-3 to uphold state consumer protection law",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.OPINION,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Official judicial vote tally and statutory ruling summary."
        ),
        AdversarialCase(
            headline = "Federal judge denies motion to dismiss antitrust lawsuit against Live Nation",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Standard legal procedural development in corporate antitrust litigation."
        ),
        AdversarialCase(
            headline = "Jury convicts former state senator on five counts of wire fraud",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Verifiable verdict announcement in criminal justice reporting."
        ),

        // Informational explainers and service journalism
        AdversarialCase(
            headline = "What you need to know about the new pension contribution rules",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.URGENCY,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Straightforward service journalism explainer; does not withhold the topic."
        ),
        AdversarialCase(
            headline = "How to apply for an international driving permit before you travel",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "How-to instructional guide without withholding an essential entity."
        ),

        // Science reporting with honest trial caveats
        AdversarialCase(
            headline = "New immunotherapy candidate shows promise in lab mice, study reports",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.OVERHYPE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Honest headline including preclinical stage ('in lab mice')."
        ),
        AdversarialCase(
            headline = "Phase 3 clinical trial of 15,000 patients shows new cholesterol drug cuts heart risk by 20 percent",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.OVERHYPE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Legitimate clinical reporting with trial phase, cohort size, and statistical outcome."
        ),
        AdversarialCase(
            headline = "FDA approves first cellular therapy for adult patients with advanced melanoma",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.OVERHYPE,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Official regulatory agency approval announcement for clinical oncology treatment."
        ),

        // Direct quotations
        AdversarialCase(
            headline = "Macron tells summit: 'Europe cannot remain a passive bystander in world trade'",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.OPINION,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Direct attributed quotation of a world leader's public speech."
        ),

        // ==========================================
        // 2. EVASION ATTACKS (Should be >= SUBTLE)
        // ==========================================

        // Forward-referencing cataphoric curiosity gaps & viral lures
        AdversarialCase(
            headline = "This is what happens when you drink three cups of green tea every morning",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Cataphoric deixis ('This is what happens') withholding the actual outcome."
        ),
        AdversarialCase(
            headline = "She opened her electricity bill and broke down in tears after seeing the total",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Withholding the bill amount paired with affective consequence ('broke down in tears')."
        ),
        AdversarialCase(
            headline = "21 brilliant ways to organize your kitchen that you'll wish you knew sooner",
            expectedLevel = FlagLevel.NONE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.FALSE_POSITIVE_TRAP,
            rationale = "Maintainer ruling 2026-11-17 (edge case 2026-11-17-g01): a service list that names its subject is fair, as in rule pack 37; the regret hook is hype, but nothing is withheld."
        ),
        AdversarialCase(
            headline = "Flight attendant shares the clever hotel door trick you should always do",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Withholding specific action paired with second-person prescriptive command ('you should always do')."
        ),
        AdversarialCase(
            headline = "The genius iPhone button trick Apple didn't tell you about",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Curiosity gap with withheld feature and conspiratorial corporate framing."
        ),
        AdversarialCase(
            headline = "TikTok user shares simple refrigerator hack that will blow your mind",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Viral lifehack lure paired with affective hyperbole ('blow your mind')."
        ),
        AdversarialCase(
            headline = "Beloved Hollywood actor finally breaks silence on devastating diagnosis",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Double-withholding formula omitting both celebrity name and medical condition."
        ),
        AdversarialCase(
            headline = "Internet loses its mind over customer's receipt at luxury restaurant",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Viral hysteria idiom ('loses its mind') withholding the actual receipt amount."
        ),

        // Conflict staging & fight talk
        AdversarialCase(
            headline = "Senator aims cryptic swipe at party leadership over border policy",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Conflict staging framing institutional disagreement as combat strike."
        ),
        AdversarialCase(
            headline = "Governor takes warning shot at federal regulators as row erupts",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Military metaphor ('takes warning shot') staging political friction."
        ),
        AdversarialCase(
            headline = "Broadcaster left humiliated on live television after guest delivers brutal reality check",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Affective humiliation staging with violent combat metaphor ('brutal reality check')."
        ),
        AdversarialCase(
            headline = "Viewers erupt in fury over controversial scene in Sunday night drama",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.RAGE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Synthetic mob outrage formulation ('erupt in fury') amplifying minor social chatter."
        ),

        // Threat without probability or demographic scope & health scares
        AdversarialCase(
            headline = "Terrifying new superbug spreading across hospitals before doctors can sound the alarm",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "High-arousal scare terms without incidence statistics or specific geography."
        ),
        AdversarialCase(
            headline = "The common breakfast food that is secretly destroying your gut health",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Withholding common dietary item paired with catastrophic visceral threat ('secretly destroying')."
        ),
        AdversarialCase(
            headline = "The silent nighttime symptom millions of adults are dangerously ignoring",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Cataphoric medical scare formula with hyperbolic scale ('millions') and threat adverb ('dangerously')."
        ),
        AdversarialCase(
            headline = "Scientists warn rogue asteroid could cause unprecedented devastation on Earth",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Vague catastrophic existential threat without astronomical designation or impact probability."
        ),

        // Urgency and panic directives
        AdversarialCase(
            headline = "If you own any of these three tech stocks, sell them immediately",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.URGENCY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Urgent imperative instruction with forward-referencing withheld list ('these three tech stocks')."
        ),
        AdversarialCase(
            headline = "Doctors urge public to immediately throw away this kitchen staple",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.URGENCY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Manufactured emergency directive paired with cataphoric withheld item ('this kitchen staple')."
        ),

        // Speculative hysteria & financial alarmism
        AdversarialCase(
            headline = "Housing market could crash by 40 percent as economists sound the alarm",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.HEDGE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Extreme hypothetical catastrophe framed with modal verb and panic sourcing."
        ),
        AdversarialCase(
            headline = "Financial expert warns Americans to prepare for catastrophic economic collapse",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.FEAR,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Vaguely sourced authority forecasting ungrounded apocalypse ('catastrophic economic collapse')."
        ),
        AdversarialCase(
            headline = "Major earthquake could strike California within days, alarming new forecast warns",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.HEDGE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Extreme disaster speculation with imminent temporal pressure and vague alarming forecast."
        ),

        // Preclinical overhype & financial lure
        AdversarialCase(
            headline = "Breakthrough molecule reverses memory loss in dementia trial",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.OVERHYPE,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Unhedged claim of reversing dementia without disclosing in vitro or murine model."
        ),
        AdversarialCase(
            headline = "The one cryptocurrency poised to make early investors rich this year",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Superlative singularity hook ('The one...') promising wealth lure without disclosing asset name."
        ),

        // Personality quiz / viral identity lure
        AdversarialCase(
            headline = "Which Character From 'Succession' Are You Based On Your Morning Routine",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Personality profiling formula hooking self-identity to arbitrary habits."
        ),

        // Prescriptive viral imperative
        AdversarialCase(
            headline = "You need to see this woman's response after finding out her flight was cancelled",
            expectedLevel = FlagLevel.SUBTLE,
            targetCategory = Category.CURIOSITY,
            challengeType = ChallengeType.EVASION_ATTACK,
            rationale = "Prescriptive second-person command withholding the actual reaction."
        )
    )
}
