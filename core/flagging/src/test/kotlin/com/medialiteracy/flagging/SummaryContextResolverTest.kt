package com.medialiteracy.flagging

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryContextResolverTest {

    private val flagger = HeadlineFlagger(RulePackLoader.loadDefault())

    @Test
    fun `extracts clean lead sentence when summary resolves curiosity gap`() {
        val headline = "Doctor reveals the 1 common breakfast food you should never eat"
        val summary = "<p>Dr. Jane Evans warned on Monday that sweetened instant oatmeal causes severe glucose spikes. The post Doctor reveals first appeared on HealthDaily.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(result.isFlagged)
        assertTrue(result.categories.contains(Category.CURIOSITY))
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("sweetened instant oatmeal"))
        assertTrue(!result.resolvedContext!!.contains("HealthDaily"))
    }

    @Test
    fun `leaves resolvedContext null on double tease where summary also teases`() {
        val headline = "Why DWTS star skipped the finale rehearsals"
        val summary = "Fans were stunned by her sudden absence. Click here to read why she decided to skip rehearsals."

        val result = flagger.flagWithSummary(headline, summary)
        assertNull(result.resolvedContext, "double-tease summaries should not be surfaced as resolutions")
    }

    @Test
    fun `ignores summary that is an exact duplicate of the headline`() {
        val headline = "Why the era of cheap government debt is over"
        val summary = "<p>Why the era of cheap government debt is over.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertNull(result.resolvedContext)
    }

    @Test
    fun `attaches the answer to a curiosity tease the headline already earned`() {
        val headline = "Michael Carrick told Man United failed to replace one player"
        val summary = "<p>Club legend Bryan Robson said Manchester United never adequately replaced midfielder Casemiro after his departure last summer.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertEquals(FlagLevel.SUBTLE, result.level)
        assertTrue(result.categories.contains(Category.CURIOSITY))
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("replaced midfielder Casemiro"))
    }

    @Test
    fun `sanitizes html entities and tracking boilerplate cleanly`() {
        val raw = "Scientists found &ldquo;microplastics&rdquo; in bottled water&hellip; Read more at SciencePost.com"
        val sanitized = SummaryContextResolver.sanitize(raw)
        assertEquals("Scientists found “microplastics” in bottled water…", sanitized)
    }

    @Test
    fun `cell lines and organoids are a lab stage too`() {
        // Rule pack 33, from the 2026-10 flag review (glioblastoma grown in two human cell lines on a chip).
        val headline = "Scientists develop new compound to reverse Alzheimer's memory loss"
        val summary = "<p>The compound restored the damaged neurons in two human cell lines grown on a microfluidic chip, the team reported.</p>"
        assertTrue(Category.OVERHYPE in flagger.flagWithSummary(headline, summary).categories)
    }

    @Test
    fun `flags science claim as OVERHYPE and extracts lab caveat when summary reveals animal testing`() {
        val headline = "Scientists develop new compound to reverse Alzheimer's memory loss"
        val summary = "<p>The experimental therapy restored cognitive function in laboratory mice after two weeks of treatment, researchers reported.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(result.isFlagged)
        assertTrue(result.categories.contains(Category.OVERHYPE))
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("in laboratory mice"))
        val hit = result.hits.firstOrNull { it.category == Category.OVERHYPE }
        assertNotNull(hit)
        assertTrue(hit.matchedText.contains("reverse Alzheimer's memory loss", ignoreCase = true))
    }

    @Test
    fun `ignores science claim when headline honestly states the caveat in mice`() {
        val headline = "New compound reverses Alzheimer's memory loss in mice"
        val summary = "<p>The experimental therapy restored cognitive function in laboratory mice after two weeks of treatment, researchers reported.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(!result.categories.contains(Category.OVERHYPE), "Honest headline caveat in mice should not trigger OVERHYPE")
    }

    @Test
    fun `attaches lab caveat to headline already flagged for OVERHYPE`() {
        val headline = "Scientists claim revolutionary pill could cure aging"
        val summary = "<p>The compound extended lifespan in rodents by twenty percent during preliminary testing at Cambridge.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(result.isFlagged)
        assertTrue(result.categories.contains(Category.OVERHYPE))
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("in rodents"))
    }

    @Test
    fun `does not falsely flag normal questions as curiosity gaps when summary answers them`() {
        // A first-person dilemma closed by a question is a tease since rule pack 29, so the plain
        // question here is an explainer the piece answers.
        val adviceHeadline = "What is El Niño and what causes it?"
        val adviceSummary = "<p>You really need to find a way to talk about this and explore the underlying issues for both of you.</p>"

        val result = flagger.flagWithSummary(adviceHeadline, adviceSummary)
        kotlin.test.assertFalse(result.isFlagged, "Normal advice questions must not be flagged as curiosity clickbait")

        val surveyHeadline = "Tell us: have you been assaulted on Great Britain's railways?"
        val surveySummary = "<p>There were 13,464 reported assaults against passengers on the mainline railway according to official data.</p>"

        val surveyResult = flagger.flagWithSummary(surveyHeadline, surveySummary)
        kotlin.test.assertFalse(surveyResult.isFlagged, "Reader callouts ending with question marks must not be flagged as curiosity clickbait")
    }

    @Test
    fun `does not falsely flag standard temporal this as curiosity gaps`() {
        val headline = "Most Americans don't know Hasan Piker. The rest say he is changing this year's elections"
        val summary = "<p>The popular Twitch streamer has been vilified by Republicans, but the socialist commentator has a large audience.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        kotlin.test.assertFalse(result.isFlagged, "Temporal 'this year' must not be flagged as curiosity clickbait")
    }

    @Test
    fun `walk back detection flags when summary de-escalates headline alarm`() {
        val headline = "Nation faces catastrophic winter blackouts amid surging power crisis"
        val summary = "<p>Officials stressed there is no immediate threat to the grid and risk of power cuts remains minimal.</p>"

        val initial = flagger.flag(headline)
        val resolved = SummaryContextResolver.resolve(headline, summary, initial)

        assertTrue(resolved.isFlagged)
        assertTrue(Category.FEAR in resolved.categories)
        assertNotNull(resolved.resolvedContext)
        assertTrue(resolved.resolvedContext!!.contains("no immediate threat"))
    }

    @Test
    fun `attribution shift never creates a flag on its own`() {
        val headline = "Tax reform is a recipe for economic devastation"
        val summary = "<p>According to shadow chancellor Rachel Reeves, the proposed cuts would destabilise public finances.</p>"
        val initial = flagger.flag(headline)
        assertFalse(initial.isFlagged, "precondition: the headline alone is not flagged")

        val resolved = SummaryContextResolver.resolve(headline, summary, initial)
        assertFalse(resolved.isFlagged)
    }

    @Test
    fun `quoted words of a named official are not turned into opinion as fact`() {
        val headline = "Blanche defends Trump's White House media ban: 'It is a privilege, not a right'"
        val summary = "<p>US attorney general argues that president was 'sick and tired' of inaccurate reporting by CNN, MS Now and Politico</p>" +
            "<p>Donald Trump's attorney general Todd Blanche argued on Sunday that the administration was permitted to ban three news outlets " +
            "from the White House because \"it is a privilege and not a right to be there\".</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertFalse(result.isFlagged, "A named official's quoted words are reported speech, not opinion stated as fact")
        assertNull(result.resolvedContext)
    }

    @Test
    fun `attribution context needs a capitalised name`() {
        val headline = "The new tax plan is a disaster for pensioners"
        assertTrue(Category.OPINION in flagger.flag(headline).categories, "precondition: the headline alone is flagged as opinion")

        val nobody = flagger.flagWithSummary(headline, "<p>Critics argue that the plan would cut pension payments by a third over the next decade.</p>")
        assertNull(nobody.resolvedContext, "'argue that the plan' names nobody")

        val named = flagger.flagWithSummary(headline, "<p>According to shadow chancellor Rachel Reeves, the plan would cut pension payments by a third.</p>")
        assertNotNull(named.resolvedContext)
        assertTrue(named.resolvedContext!!.contains("Rachel Reeves"))
    }

    @Test
    fun `curiosity context never creates a flag on its own`() {
        val headline = "Commuters told to avoid this route during Sunday rail works"
        val summary = "<p>Network Rail said the line between Leeds and York will close on Sunday for signal upgrades, " +
            "with replacement buses running every 20 minutes.</p>"
        assertFalse(flagger.flag(headline).isFlagged, "precondition: the headline alone is not flagged")

        val result = flagger.flagWithSummary(headline, summary)
        assertFalse(result.isFlagged, "A one-word overlap with the summary must not turn a headline into a curiosity gap")
    }

    @Test
    fun `resolver hits always point at real words, never the whole headline`() {
        val cases = listOf(
            "Scientists develop new compound to reverse Alzheimer's memory loss" to
                "<p>The experimental therapy restored cognitive function in laboratory mice after two weeks of treatment, researchers reported.</p>",
            "Nation faces catastrophic winter blackouts amid surging power crisis" to
                "<p>Officials stressed there is no immediate threat to the grid and risk of power cuts remains minimal.</p>",
        )
        for ((headline, summary) in cases) {
            val result = flagger.flagWithSummary(headline, summary)
            assertTrue(result.isFlagged, "precondition: '$headline' is flagged through its summary")
            for (hit in result.hits) {
                assertTrue(hit.range.last - hit.range.first + 1 < headline.length, "Hit '${hit.matchedText}' covers the whole headline")
                assertEquals(headline.substring(hit.range), hit.matchedText)
            }
        }
    }

    @Test
    fun `trailing debate nudge with byline is signed opinion and left alone`() {
        // Codebook rule 2: the byline tells the reader whose view it is, so the
        // nudge is not opinion presented as fact and there is no flag to resolve.
        // The unsigned case below still extracts the stance.
        val headline = "Tony Blair would like Andy Burnham to take his advice on rejoining the EU. Should he? | Tom Baldwin"
        val summary = "<p>Tom Baldwin argues that Labour must reject Blair's outdated stance and focus on domestic economic renewal. The post first appeared on The Guardian.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(Category.OPINION !in result.categories, "A signed column's nudge is not opinion as fact")
        assertFalse(result.isFlagged)
    }

    @Test
    fun `trailing debate nudge without byline extracts editorial stance answering the question`() {
        val headline = "Kamala Harris is belatedly courting progressives. Is it too little, too late?"
        val summary = "<p>Party strategists argue that the outreach comes far too late to sway skeptical progressive voters ahead of the election.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(result.isFlagged)
        assertTrue(Category.QUESTION in result.categories)
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("comes far too late to sway skeptical progressive voters"))
    }

    @Test
    fun `Betteridge superlative question extracts resolving negative answer over generic background sentence`() {
        val headline = "Could this really be the world's worst movie ever made?"
        val summary = "<p>The much-anticipated movie opened to packed theaters nationwide on Friday.</p>" +
            "<p>Critics overwhelmingly concluded the answer is an emphatic no, despite several clunky dialogue scenes.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(result.isFlagged)
        assertTrue(Category.QUESTION in result.categories)
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("the answer is an emphatic no"))
        assertFalse(result.resolvedContext!!.contains("packed theaters nationwide"))
    }

    @Test
    fun `speculative question extracts resolving debunking answer from experts`() {
        val headline = "Is the national power grid on the brink of failure?"
        val summary = "<p>Energy analysts confirmed there is no evidence that domestic consumption poses an immediate threat to grid capacity.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertTrue(result.isFlagged)
        assertTrue(Category.HEDGE in result.categories)
        assertNotNull(result.resolvedContext)
        assertTrue(result.resolvedContext!!.contains("there is no evidence that domestic consumption poses"))
    }

    @Test
    fun `straight news questions remain unflagged when summary answers them`() {
        val headline = "Did the Federal Reserve lower interest rates at its September meeting?"
        val summary = "<p>The Federal Reserve lowered benchmark borrowing costs by a half percentage point on Wednesday.</p>"

        val result = flagger.flagWithSummary(headline, summary)
        assertFalse(result.isFlagged, "Straight news questions must remain unflagged even when summary answers the question")
        assertNull(result.resolvedContext)
    }
}

