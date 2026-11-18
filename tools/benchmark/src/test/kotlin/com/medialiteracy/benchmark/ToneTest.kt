package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ToneTest {
    private fun temp(text: String) = File.createTempFile("tone", ".csv").apply { deleteOnExit(); writeText(text) }

    @Test
    fun `reads BABE rows, skipping no-agreement labels`() {
        val csv = "\uFEFFtext;news_link;outlet;topic;type;label_bias;label_opinion;biased_words\n" +
            "\"\"\"We have one beautiful law,\"\" Trump said in his characteristically bizarre syntax.\";https://x;Alternet;guns;left;Biased;Opinion;['bizarre', 'characteristically']\n" +
            "The council met on Monday.;https://y;Fox News;local;right;Non-biased;Entirely factual;[]\n" +
            "Something unclear.;https://z;CNN;local;left;No agreement;Unclear;[]\n"
        val cases = ToneSources.readBabe(temp(csv))
        assertEquals(2, cases.size)
        assertEquals(listOf("bizarre", "characteristically"), cases[0].biasedWords)
        assertEquals(true, cases[0].toned)
        assertEquals(false, cases[1].toned)
        assertEquals(listOf("left", "right"), cases.map { it.group })
    }

    @Test
    fun `splits tone tallies by the dataset's own lean label`() {
        val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
        val cases = listOf(
            SentenceToneCase("a", "Families face a ticking timebomb of side effects", toned = true, group = "left"),
            SentenceToneCase("b", "The council approved the budget on Monday.", toned = false, group = "right"),
            SentenceToneCase("c", "No lean recorded for this one.", toned = false),
        )
        val byGroup = ToneScorer.scoreByGroup(cases, flagger)
        assertEquals(listOf("left", "right"), byGroup.keys.toList())
        assertEquals(1, byGroup.getValue("left").values.sumOf { it.tp })
        assertEquals(1, byGroup.getValue("right").values.sumOf { it.tn })
    }

    @Test
    fun `reads CheckThat subjectivity labels`() {
        val tsv = "sentence_id\tsentence\tlabel\nid1\tThe budget passed.\tOBJ\nid2\tIt is a shameful budget.\tSUBJ\n"
        val cases = ToneSources.readCheckThat(temp(tsv))
        assertEquals(listOf(false, true), cases.map { it.toned })
    }

    @Test
    fun `counts sentence and word agreement`() {
        val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
        val cases = listOf(
            SentenceToneCase("a", "Families face a ticking timebomb of side effects", toned = true, biasedWords = listOf("ticking timebomb")),
            SentenceToneCase("b", "The council approved the budget on Monday.", toned = false),
        )
        val tallies = ToneScorer.score(cases, flagger, hasWordLabels = true).values
        assertEquals(1, tallies.sumOf { it.tp })
        assertEquals(1, tallies.sumOf { it.tn })
        assertEquals(1, tallies.sumOf { it.biasedWordCovered })
    }
}
