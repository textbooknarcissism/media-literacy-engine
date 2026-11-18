package com.medialiteracy

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePackLoader
import kotlin.test.Test

/** Regression checks on headlines read with their summaries: an op-ed and a science question must stay unflagged. */
class UserHeadlinesTest {

    @Test
    fun testUserHeadlines() {
        val flagger = HeadlineFlagger(RulePackLoader.loadDefault())
        val h1 = "I have seen with pride how British overseas aid saves childrens' lives. That is why I say cuts should be reversed | David Harewood"
        val h2 = "Can Species Evolve Fast Enough to Survive Climate Change?"
        val h3 = "Tony Blair would like Andy Burnham to take his advice on rejoining EU. Should He?"

        val s1 = "<p>I understand the pressure on the public purse, but I have also seen the effect of our spending on the ground. Our choices can do so much good</p>"
        val s2 = "<p>Scientists study how quickly species can adapt to rising global temperatures and shifting weather patterns.</p>"
        val s3 = "<p>Former prime minister says Labour should prepare ground for closer relationship with Brussels.</p>"

        val r1 = flagger.flagWithSummary(h1, s1)
        println("H1 flagWithSummary: level=${r1.level} categories=${r1.categories} hits=${r1.hits.map { "${it.category}: '${it.matchedText}'" }} context='${r1.resolvedContext}'")
        kotlin.test.assertFalse(r1.isFlagged, "David Harewood op-ed should not be flagged as Withheld detail")

        val r2 = flagger.flagWithSummary(h2, s2)
        println("H2 flagWithSummary: level=${r2.level} categories=${r2.categories} hits=${r2.hits.map { "${it.category}: '${it.matchedText}'" }} context='${r2.resolvedContext}'")
        kotlin.test.assertFalse(r2.isFlagged, "Science question should not be flagged as Withheld detail")

        val r3 = flagger.flagWithSummary(h3, s3)
        println("H3 flagWithSummary: level=${r3.level} categories=${r3.categories} hits=${r3.hits.map { "${it.category}: '${it.matchedText}'" }} context='${r3.resolvedContext}'")
    }
}
