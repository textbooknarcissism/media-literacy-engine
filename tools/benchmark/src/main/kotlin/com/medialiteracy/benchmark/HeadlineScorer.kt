package com.medialiteracy.benchmark

import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.FlagResult

data class Scored(val case: HeadlineCase, val got: FlagResult, val part: SplitPart)

class HeadlineReport(val dataset: HeadlineDataset, val scored: List<Scored>) {

    fun levels(part: SplitPart?, where: (HeadlineCase) -> Boolean = { true }): LevelTally =
        LevelTally().also { tally ->
            scored.filter { (part == null || it.part == part) && where(it.case) }
                .forEach { tally.add(it.case.expectedLevel, it.got.level) }
        }

    /** Null when this dataset has no technique labels. */
    fun techniques(part: SplitPart?): TechniqueTally? {
        val labelled = scored.filter { (part == null || it.part == part) && it.case.expectedTechniques != null }
        if (labelled.isEmpty()) return null
        return TechniqueTally().also { tally ->
            labelled.forEach { tally.add(it.case.expectedTechniques!!, it.got.categories.toSet()) }
        }
    }
}

/** How much of the story the engine is given: [NONE] scores the headline alone, to measure what context adds. */
enum class ContextMode(val id: String) {
    NONE("none"),
    SUMMARY("summary"),
    STORY("story");

    companion object {
        fun fromId(id: String): ContextMode =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown context '$id' (use none, summary, or story)")
    }
}

object HeadlineScorer {
    /** Uses the context (summary, page metadata, and story text) according to [context] mode. */
    fun score(
        dataset: HeadlineDataset,
        cases: List<HeadlineCase>,
        flagger: HeadlineFlagger,
        context: ContextMode = ContextMode.SUMMARY,
    ): HeadlineReport =
        HeadlineReport(dataset, cases.map { case ->
            val type = ArticleType.fromId(case.articleType)
            val articleCtx = when (context) {
                ContextMode.NONE -> ArticleContext(articleType = type)
                ContextMode.SUMMARY -> ArticleContext(
                    articleType = type,
                    summary = case.summary,
                    pageSection = case.pageSection,
                    pageType = case.pageType,
                )
                ContextMode.STORY -> ArticleContext(
                    articleType = type,
                    summary = case.summary,
                    pageSection = case.pageSection,
                    pageType = case.pageType,
                    storyText = case.storyText,
                )
            }
            val got = flagger.flag(case.headline, articleCtx)
            Scored(case, got, dataset.partOf(case.id))
        })
}
