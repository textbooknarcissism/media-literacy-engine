package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel

/** One headline with the verdict its dataset expects. */
data class HeadlineCase(
    val id: String,
    val headline: String,
    val expectedLevel: FlagLevel,
    val outlet: String,
    /** Null when the dataset labels only strength, not which techniques. */
    val expectedTechniques: Set<Category>? = null,
    val articleType: String? = null,
    /** "publisher-tag" when the outlet itself labeled the type; "section-feed" when inferred from the feed it came from. */
    val typeSource: String? = null,
    val summary: String? = null,
    val link: String? = null,
    /** The kind of coverage the headline comes from ("gaming", "local"...), where the dataset records it. */
    val beat: String? = null,
    val pageSection: String? = null,
    val pageType: String? = null,
    val storyText: String? = null,
)

/** One news sentence from a tone dataset. [toned] is the annotators' verdict (biased or subjective). */
data class SentenceToneCase(
    val id: String,
    val sentence: String,
    val toned: Boolean,
    /** Words the annotators marked as biased, where the dataset has them. */
    val biasedWords: List<String> = emptyList(),
    /** The dataset's own lean label for the outlet ("left", "center", "right"), where it has one. */
    val group: String? = null,
)

/** One clickbait post, its article's own description, and the answer the post withheld. */
data class TeaseCase(
    val id: String,
    val tease: String,
    val summary: String,
    val spoilers: List<String>,
)

fun headlineCaseId(headline: String): String = sha256Hex(headline.trim().lowercase())
