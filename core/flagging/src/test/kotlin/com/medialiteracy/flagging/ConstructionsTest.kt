package com.medialiteracy.flagging

import com.medialiteracy.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rule pack 35: a flag needs a construction, not a word, and names one clear
 * technique. The headlines are ones seen in the Know News app in
 * November 2026, or of the same shape.
 */
class ConstructionsTest {
    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    @Test
    fun `a word used literally does not flag a headline`() {
        for (headline in listOf(
            "FromSoftware temporarily shuts down Dark Souls 2 and 3 multiplayer over 'server issue'",
            "Assassin's Creed Black Flag Resynced's final major update adds long-awaited New Game Plus, new Nightmare difficulty and much more",
            "Frights, Fangs, and Healthcare Nightmares in Winning Halloween Haikus",
            "Silver Pines is a great survival horror throwback with an unexpected Metroidvania twist, but it makes me glad the genre's moved on",
            "Urgent toxic air warning issued as air quality map turns red and purple over London",
            "Maresca's dream start turns into nightmare as title hopes hit",
        )) {
            assertFalse(flagger.flag(headline).isFlagged, headline)
        }
    }

    @Test
    fun `a flag names the technique that earned it and nothing weaker`() {
        val epipen = flagger.flag("She Needed an EpiPen. But First, an Urgent Care Asked Her To Pay.")
        assertEquals(setOf(Category.CURIOSITY), epipen.categories.toSet(), "Urgent Care is a clinic, not urgency")

        val slop = flagger.flag("Anthropic updates Claude to 'steer it away' from letting users clone games following weeks of Steam being inundated with AI slop")
        assertEquals(setOf(Category.OPINION), slop.categories.toSet(), "a named company's quoted words are not unnamed sources")

        val finale = flagger.flag("Maya Jama on That Shocking 'Celebrity Traitors' Finale, [SPOILER]'s Betrayal and 'Heated' Last Roundtable: 'My Heart Was Jumping Out of My Chest'")
        assertFalse(Category.URGENCY in finale.categories, "[SPOILER] is an editor's tag, not shouting")
    }

    @Test
    fun `a general noun standing where the fact should be is a tease`() {
        for (headline in listOf(
            "Ninja's AutoBarista is impressively versatile, but one feature was a dealbreaker for me",
            "Amanda Seyfried Reveals Her Daughter's Reaction to Mean Girls",
            "Love Is Blind Star Michaella Giorgio's Romance With Costar Revealed",
            "Professor tells BBC the 1 'most concerning' impact of polar vortex in UK - not snow",
            "Study Identifies The #1 Issue Affecting The Academic Performance of US College Students",
            "Costco makes controversial change to its $1.50 hot dog combo",
            "Harry Potter Series Renewed for Season 3, but Not the One You Think",
            "Derby mum rips up son's bedroom carpet and can't get over what pours out",
            "Popular Supplement Linked to Faster Alzheimer's Progression",
        )) {
            assertTrue(Category.CURIOSITY in flagger.flag(headline).categories, headline)
        }
    }

    @Test
    fun `a headline that fills the slot is left alone`() {
        for (headline in listOf(
            "In Fractious France, Politicians Agree on One Thing: Pope Leo Was a Hit",
            "How to Whiten White Sheets With 2 Ingredients",
            "Thomas Tuchel sends Harry Maguire brutal verdict - 'I didn't like it and it doesn't help'",
            "UK and EU confirm date of summit after talks",
            "Brussels unveils plans for EU-wide social media ban for kids under 13",
        )) {
            assertFalse(flagger.flag(headline).isFlagged, headline)
        }
    }

    @Test
    fun `conflict, fear and opinion words used literally are left alone`() {
        for (headline in listOf(
            "Ukraine destroys Russian ammunition depot",
            "Arsonist torches three cars outside police station",
            "Protesters carry torches through city centre",
            "Dog savages toddler in park",
            "Miners blasted through 200 metres of rock",
            "Swipe left: the dating app adding a new feature",
            "Shoppers can now swipe card at every till",
            "How to write your first Bash script",
            "Toxic fumes force evacuation of school",
            "Fukushima meltdown: what we know 15 years on",
            "Cars torched in third night of riots",
            "E. coli outbreak sickening dozens across three states",
            "Chef offers pasta masterclass at food festival",
            "Court ruling could have chilling effect on free speech",
            "Fast & Furious 11 arrives in cinemas on 4 April",
        )) {
            assertFalse(flagger.flag(headline).isFlagged, headline)
        }
    }

    @Test
    fun `the same words in their conflict constructions still flag`() {
        for (headline in listOf(
            "Critics in meltdown over US cheese giant Tillamook halal label",
            "School's 'no touching' rule blasted after pupils 'handed detentions for high-fiving'",
            "Stacey Solomon fumes at claims she 'skipped queue' at Pokemon card event",
            "Trump fumes over ‘destructive’ Supreme Court judges",
            "Lee Andrews swipes at ex Katie Price as he 'looks for younger model' after split",
            "Jasmine Crockett torched for honoring singer with notorious history of violence: 'Completely vile'",
            "Senator destroys rival in fiery debate",
            "Minister DESTROYS heckler at party conference",
        )) {
            assertTrue(Category.RAGE in flagger.flag(headline).categories, headline)
        }
    }
}
