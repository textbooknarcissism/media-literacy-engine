package com.medialiteracy.model

/**
 * The eleven headline techniques the Media Literacy Engine can recognize.
 *
 * Wording note: every user-facing string here describes the *technique*, never
 * the outlet or the journalist. The engine names a pattern and stops there; it
 * does not tell anyone their reporting is bad.
 *
 * This engine is a media literacy tool, so the wording here is the product,
 * not labeling on top of it. Three rules it follows:
 *
 * 1. **A name an ordinary person understands on sight.** "Curiosity gap" and
 *    "speculative framing" are media-studies terms. Someone who already knows
 *    them did not need the tool.
 * 2. **Say what the writer did, in one short sentence.** Not what effect it has
 *    on the brain: that is a lecture, and it is unprovable.
 * 3. **[askYourself] is the part that teaches.** Naming a trick tells someone
 *    about this one headline. Handing them the question to ask lets them catch
 *    the next one without the tool. That is the whole point, so it is a field
 *    and not an afterthought.
 */
enum class Category(
    /** Stable name used in the rule file and in saved settings. Never change these. */
    val id: String,
    /** Shown next to the flag, e.g. "Unnamed sources". */
    val displayName: String,
    /** One plain sentence saying what the writer did. Shown on a full flag. */
    val explanation: String,
    /** The same thing, softer, shown on a quiet notice where the engine is less sure. */
    val cautionExplanation: String,
    /**
     * The question the reader should ask themselves: here and next time,
     * without the tool. Written in second person, and always answerable by
     * looking at the headline.
     */
    val askYourself: String,
    /**
     * Plain explanation of the craft incentive behind the technique: why writers
     * use it to drive clicks, shares, or engagement, without moralizing or lecturing.
     */
    val whyItWorks: String,
) {
    FEAR(
        id = "manufactured_fear",
        displayName = "Alarmist framing",
        explanation = "Uses frightening words without establishing who is at risk, how severe it is, or how likely it is to occur.",
        cautionExplanation = "Reaches for an alarmist word where measured reporting would suffice.",
        askYourself = "Does it say who is actually at risk, and how likely it is?",
        whyItWorks = "Uncertain threat language triggers an urgent scanning reflex: readers click immediately to find out if the danger applies to them or their family.",
    ),
    RAGE(
        id = "manufactured_rage",
        displayName = "Conflict framing",
        explanation = "Stages political, cultural, or personal disagreements as physical combat or warfare.",
        cautionExplanation = "Uses a combat word for an ordinary policy debate or disagreement.",
        askYourself = "Did anyone actually attack anyone, or did they just disagree?",
        whyItWorks = "Combat metaphors turn policy debates into tribal contests, driving high-arousal social sharing and comments from readers eager to back their side.",
    ),
    HEDGE(
        id = "speculative_hedge",
        displayName = "Speculation",
        explanation = "Reports hypothetical future scenarios as active news using modal words like could, may, or might.",
        cautionExplanation = "Says could or may, so the event has not actually happened yet.",
        askYourself = "Has this actually happened, or might it happen?",
        whyItWorks = "Modal verbs let an outlet run dramatic hypothetical scenarios as active news while protecting themselves from liability if the scenario never occurs.",
    ),
    SOURCING(
        id = "vague_sourcing",
        displayName = "Unnamed sources",
        explanation = "Rests major claims on unnamed insiders, rumors, or uncheckable sources.",
        cautionExplanation = "Mentions sources or insiders without saying who they are.",
        askYourself = "Who said this, and could you go and check them?",
        whyItWorks = "Anonymous attribution lends institutional authority to rumors or leaks without holding any named individual accountable for the truth of the claim.",
    ),

    /**
     * Split from [SOURCING] in rule pack 38 (codebook 13), because the study is often named further down the story, so "who is
     * it?" was the wrong question. The trouble is the one finding picked to
     * fit the headline. The engine cannot see that from the headline, so the
     * wording names the move and hands the reader the check, without
     * accusing anyone of cherry-picking.
     *
     * The one deliberate exception to the rule above that [askYourself] is
     * answerable from the headline: what else a study found is only in the
     * story, and that is the place to look.
     */
    STUDY(
        id = "study_as_proof",
        displayName = "Study as proof",
        explanation = "Presents a study or poll as the proof of its point, without saying whose it is or what else it found.",
        cautionExplanation = "Credits a study or poll without saying whose it is.",
        askYourself = "What did the study actually measure, and does the story tell you what else it found, or only the part that fits the headline?",
        whyItWorks = "A study sounds like settled science, so one result picked to fit the story can borrow the authority of the whole study.",
    ),
    CURIOSITY(
        id = "curiosity_gap",
        displayName = "Withheld detail",
        explanation = "Deliberately leaves out the central fact from the headline to compel a click.",
        cautionExplanation = "Leaves out a name or key fact it could easily have included.",
        askYourself = "What has been left out, and could it have been said here?",
        whyItWorks = "Withholding the central fact creates an information void: curiosity compels a click to achieve closure that a complete headline would make unnecessary.",
    ),
    URGENCY(
        id = "urgency_pressure",
        displayName = "Manufactured urgency",
        explanation = "Imposes artificial time pressure or scarcity on a story that has none.",
        cautionExplanation = "Uses rush wording on something that is not time-sensitive.",
        askYourself = "Is there a deadline in this story, or does it just sound like there is?",
        whyItWorks = "Manufactured deadlines prompt impulsive reading before the reader can evaluate whether the information is actually important or relevant.",
    ),
    OVERHYPE(
        id = "science_overhype",
        displayName = "Early science",
        explanation = "Presents lab-bench, animal, or simulation research as a breakthrough before human clinical trials.",
        cautionExplanation = "Reports early-stage research without disclosing how far along it is.",
        askYourself = "Has this been tested on people, or only in a lab?",
        whyItWorks = "Staging laboratory or animal trials as human breakthroughs generates widespread viral hope, while burying the years of trial hurdles deep in the text.",
    ),
    OPINION(
        id = "opinion_as_fact",
        displayName = "Opinion as fact",
        explanation = "Presents the writer's subjective editorial judgment as an established event.",
        cautionExplanation = "States a personal judgment without marking it as opinion.",
        askYourself = "Is this something that happened, or somebody's view of it?",
        whyItWorks = "Presenting subjective commentary as established fact bypasses skepticism, steering how the reader feels about the event before they even read the reporting.",
    ),

    /**
     * Split from [OPINION] in rule pack 39 (codebook 14), at the owner's
     * request: a question states nothing, so "is this a fact or a view?" is
     * the wrong check. The move is a claim, verdict or worry carried inside a
     * question, so the outlet can say it only asked. The check hands the
     * reader the claim back as a statement.
     */
    QUESTION(
        id = "loaded_question",
        displayName = "Loaded question",
        explanation = "Asks a question that carries a claim, verdict or worry the headline does not state outright.",
        cautionExplanation = "Puts a claim in the form of a question.",
        askYourself = "Turn the question into a statement: would the outlet print that, and does the story back it up?",
        whyItWorks = "A question lets a headline plant a claim in the reader's mind while the outlet can say it only asked.",
    ),

    /**
     * Added in rule pack 40 (codebook 15): a crowd of
     * unknown size reported as the news ("Fans are furious", "People are
     * just realizing", "The internet reacts"). Often the story rests on a
     * handful of social media posts. The headline cannot show how many
     * people there were, so the wording names the move and hands the reader
     * the question, without saying the crowd was small.
     */
    CROWD(
        id = "social_scraping",
        displayName = "Social scraping",
        explanation = "Reports what \"people\", \"fans\" or \"the internet\" are saying or doing, without saying how many or where.",
        cautionExplanation = "Presents a crowd of unknown size as the news.",
        askYourself = "How many people is this, and where? Stories like this are often built from a few social media posts.",
        whyItWorks = "A few posts reported as what \"fans\" or \"people\" think read like a groundswell, so a handful of strangers' reactions passes for news.",
    );

    companion object {
        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}
