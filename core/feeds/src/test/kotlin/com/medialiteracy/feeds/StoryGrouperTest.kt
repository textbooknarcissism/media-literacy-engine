package com.medialiteracy.feeds

import com.medialiteracy.model.FeedItem
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every headline in here is real, taken from `fixtures/headlines/harvested.tsv`.
 *
 * The two "must not group" tests are the important ones. Both are mistakes this
 * code actually made before the distinctive-word rule existed, and both looked
 * completely reasonable until you read the headlines properly.
 */
class StoryGrouperTest {

    private val noon: Instant = Instant.parse("2026-09-16T12:00:00Z")

    private fun item(title: String, at: Instant? = null) =
        FeedItem(title = title, link = null, publishedAt = at ?: noon, summary = null, id = null)

    // --- The case the whole feature exists for -----------------------------

    @Test
    fun `groups four outlets covering one story`() {
        val groups = StoryGrouper.group(
            mapOf(
                "BBC News" to listOf(item("EU chief backs plan for Canada to become 'associate member'")),
                "The Guardian" to listOf(item("European Union invites Canada to become first ever 'associate member'")),
                "UPI" to listOf(item("EU invites Canada to become its first 'associate member'")),
                "Daily Mail" to listOf(
                    item(
                        "Brussels hit with furious backlash after top Eurocrat Ursula von der Leyen " +
                            "unveils unprecedented plan for Canada to become 'associate member' of the " +
                            "bloc without asking EU capitals first, with diplomats branding her 'bombastic'",
                    ),
                ),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(4, groups.single().sourceCount)
        assertTrue("canada" in groups.single().subjectWords)
    }

    @Test
    fun `keeps the order it was given`() {
        val first = item("'Putney pusher' suspect found dead at home")
        val second = item("Suspect in 'Putney pusher' case found dead")
        val third = item("Suspect in 'Putney pusher' case found dead at his west London home")

        val groups = StoryGrouper.group(
            mapOf(
                "BBC News" to listOf(first),
                "Sky News" to listOf(second),
                "The Guardian" to listOf(third),
            ),
        )

        // No sorting: preserved in the order feeds provided
        // them, so the plain and the loaded version stay next to each other.
        assertEquals(listOf(first, second, third), groups.single().items)
    }

    // --- The two real false matches ----------------------------------------

    @Test
    fun `does not merge two different missing person stories`() {
        // Shares "body", "found", "search", "missing": four ordinary words and
        // a completely different person, city and age.
        val groups = StoryGrouper.group(
            mapOf(
                "BBC News" to listOf(item("Body found in search for missing three-year-old Noah Woods")),
                "Daily Express" to listOf(item("Body found in search for missing Nottingham woman, 28, after 11 days")),
            ),
        )

        assertTrue(groups.isEmpty(), "two unrelated missing-person stories were merged: $groups")
    }

    @Test
    fun `does not merge a VAR error with a player profile`() {
        // Shared "inside" and "mistake". Nothing else in common at all.
        val groups = StoryGrouper.group(
            mapOf(
                "BBC News" to listOf(item("'Let's go, let's go' - inside catastrophic derby VAR mistake")),
                "Daily Mail" to listOf(
                    item(
                        "'He went into oblivion mode': Inside Raheem Sterling's terrifying fall from " +
                            "grace as he admits dangerous driving - the controversy he never recovered from",
                    ),
                ),
            ),
        )

        assertTrue(groups.isEmpty(), "a VAR story was merged with a player profile: $groups")
    }

    // --- Counting ----------------------------------------------------------

    @Test
    fun `counts outlets not articles`() {
        // The Guardian genuinely ran three of these in one day. The screen says
        // "N outlets covered this", so counting articles would be a lie.
        val groups = StoryGrouper.group(
            mapOf(
                "The Guardian" to listOf(
                    item("Ed Sheeran support acts quit after Macklemore 'Free Palestine' row"),
                    item("All supporting acts withdraw from Ed Sheeran's tour in solidarity with Macklemore"),
                ),
                "Daily Mail" to listOf(
                    item("Ed Sheeran should 'cancel his tour' after Macklemore 'Free Palestine' row, insists activist Billy Bragg"),
                ),
            ),
        )

        val group = groups.single()
        assertEquals(2, group.sourceCount)
        assertEquals(3, group.items.size)
    }

    @Test
    fun `ignores a story only one outlet covered`() {
        val groups = StoryGrouper.group(
            mapOf(
                "The Guardian" to listOf(
                    item("Ed Sheeran support acts quit after Macklemore 'Free Palestine' row"),
                    item("All supporting acts withdraw from Ed Sheeran's tour in solidarity with Macklemore"),
                ),
            ),
        )

        assertTrue(groups.isEmpty(), "one outlet writing twice is not several outlets: $groups")
    }

    // --- Time --------------------------------------------------------------

    @Test
    fun `does not group the same wording weeks apart`() {
        val groups = StoryGrouper.group(
            mapOf(
                "UPI" to listOf(item("Kosovo ex-president Hashim Thaci sentenced to 25 years for war crimes", noon)),
                "BBC News" to listOf(
                    item(
                        "Kosovo's former president sentenced to 25 years for war crimes",
                        noon.plus(Duration.ofDays(30)),
                    ),
                ),
            ),
        )

        assertTrue(groups.isEmpty(), "a month apart is not the same story: $groups")
    }

    @Test
    fun `still groups within the window`() {
        val groups = StoryGrouper.group(
            mapOf(
                "UPI" to listOf(item("Kosovo ex-president Hashim Thaci sentenced to 25 years for war crimes", noon)),
                "BBC News" to listOf(
                    item(
                        "Kosovo's former president sentenced to 25 years for war crimes",
                        noon.plus(Duration.ofHours(6)),
                    ),
                ),
            ),
        )

        assertEquals(2, groups.single().sourceCount)
    }

    @Test
    fun `a missing date does not lose the group`() {
        // Some feeds leave the date out. Dropping those articles would lose
        // real groups for a reason the reader can do nothing about.
        val groups = StoryGrouper.group(
            mapOf(
                "UPI" to listOf(item("Kosovo ex-president Hashim Thaci sentenced to 25 years for war crimes", noon)),
                "BBC News" to listOf(
                    FeedItem(
                        title = "Kosovo's former president sentenced to 25 years for war crimes",
                        link = null,
                        publishedAt = null,
                        summary = null,
                        id = null,
                    ),
                ),
            ),
        )

        assertEquals(2, groups.single().sourceCount)
    }

    // --- Edges -------------------------------------------------------------

    @Test
    fun `curly and straight quotes match`() {
        val groups = StoryGrouper.group(
            mapOf(
                "The Guardian" to listOf(item("Suspect in ‘Putney pusher’ case found dead at his west London home")),
                "Sky News" to listOf(item("Suspect in 'Putney pusher' case found dead")),
            ),
        )

        assertEquals(2, groups.single().sourceCount)
    }

    @Test
    fun `too short to judge is left alone`() {
        val groups = StoryGrouper.group(
            mapOf(
                "Sky News" to listOf(item("Man dies")),
                "BBC News" to listOf(item("Man dies")),
            ),
        )

        assertTrue(groups.isEmpty(), "two words is not enough to group on: $groups")
    }

    @Test
    fun `a feed repeating itself is not two articles`() {
        // Seen on live feeds: the BBC served this headline twice in one fetch.
        val groups = StoryGrouper.group(
            mapOf(
                "BBC News" to listOf(
                    item("Hyrox athlete apologises for continuing race after soiling herself"),
                    item("Hyrox athlete apologises for continuing race after soiling herself"),
                ),
                "Daily Mail" to listOf(
                    item("Aussie HYROX athlete apologises after soiling herself during competition"),
                ),
            ),
        )

        assertEquals(2, groups.single().items.size, "the repeated headline was shown twice")
    }

    @Test
    fun `empty feed is fine`() {
        assertTrue(StoryGrouper.group(emptyMap()).isEmpty())
    }

    @Test
    fun `groups US outlets writing in Title Case on same story`() {
        val groups = StoryGrouper.group(
            mapOf(
                "New York Times" to listOf(item("Boeing Strike Ends as Machinists Approve New Contract")),
                "CNN" to listOf(item("Boeing Machinists Ratify Deal to End Crippling Strike")),
            ),
        )

        assertEquals(1, groups.size, "Title Case headlines on Boeing strike should group together")
        val group = groups.single()
        assertEquals(2, group.sourceCount)
        assertTrue("boeing" in group.subjectWords)
    }

    @Test
    fun `does not group unrelated Title Case headlines`() {
        val groups = StoryGrouper.group(
            mapOf(
                "New York Times" to listOf(item("Federal Reserve Holds Interest Rates Steady")),
                "CNN" to listOf(item("Supreme Court Hears Arguments in Major Free Speech Case")),
            ),
        )

        assertTrue(groups.isEmpty(), "Unrelated Title Case headlines must not group")
    }
}
