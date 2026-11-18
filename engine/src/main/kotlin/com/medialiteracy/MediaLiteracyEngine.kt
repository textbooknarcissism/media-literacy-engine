package com.medialiteracy

import com.medialiteracy.feeds.FeedParser
import com.medialiteracy.feeds.StoryFramingComparator
import com.medialiteracy.feeds.StoryGrouper
import com.medialiteracy.flagging.HeadlineFlagger
import com.medialiteracy.flagging.RulePack
import com.medialiteracy.flagging.RulePackLoader
import com.medialiteracy.model.ArticleContext
import com.medialiteracy.model.ArticleType
import com.medialiteracy.model.FeedItem
import com.medialiteracy.model.FlagResult
import com.medialiteracy.model.ParsedFeed

/**
 * Primary unified entrypoint for the Media Literacy Engine SDK.
 *
 * Encapsulates headline flagging, feed ingestion, story clustering, and framing comparison
 * behind an intuitive, thread-safe, 100% offline, zero-telemetry API.
 */
class MediaLiteracyEngine(
    val rulePack: RulePack = RulePackLoader.loadDefault(),
    val flagger: HeadlineFlagger = HeadlineFlagger(rulePack),
) {

    /**
     * Flags a headline for sensationalist or manipulative framing.
     *
     * Pass [articleType] when the feed or link says what kind of piece it is
     * (see [com.medialiteracy.feeds.ArticleTypeClassifier]): an opinion piece,
     * editorial, review or letter is never charged with opinion as fact
     * (codebook rule 2). [sourceName] only selects the user's own muted
     * phrases; it never changes how a headline is judged.
     */
    fun flag(headline: String, sourceName: String? = null, articleType: ArticleType? = null): FlagResult =
        flagger.flag(headline, sourceName, articleType)

    /**
     * Flags a headline with contextual article information (such as section, page schema type,
     * author byline, summary, or story text).
     *
     * Pure, offline, and portable. Settles facts (such as opinion/review section or summary resolution),
     * never adds artificial techniques.
     */
    fun flag(headline: String, context: ArticleContext, sourceName: String? = null): FlagResult =
        flagger.flag(headline, context, sourceName)

    /** Parses an RSS or Atom feed XML string into strongly typed [ParsedFeed]. */
    fun parseFeed(xml: String): ParsedFeed =
        FeedParser.parse(xml)

    /** Groups articles covering the same story across multiple outlets. */
    fun groupStories(itemsBySource: Map<String, List<FeedItem>>): List<StoryGrouper.StoryGroup> =
        StoryGrouper.group(itemsBySource)

    /** Compares framing across different headlines covering the same story cluster. */
    fun compareFraming(
        group: StoryGrouper.StoryGroup,
        sourceNames: Map<FeedItem, String> = emptyMap(),
    ): StoryFramingComparator.StoryContrastReport {
        val triples = group.items.map { item ->
            val source = sourceNames[item] ?: "News"
            val flag = flagger.flag(item.title, source)
            Triple(item.title, source, flag)
        }
        return StoryFramingComparator.compare(items = triples)
    }

    /** Compares framing directly from a list of (headline, sourceName, flagResult) triples. */
    fun compareFraming(
        storyId: String? = null,
        items: List<Triple<String, String, FlagResult>>,
    ): StoryFramingComparator.StoryContrastReport =
        StoryFramingComparator.compare(storyId, items)

    companion object {
        /** Default singleton instance loaded with bundled rulepack. */
        val default: MediaLiteracyEngine by lazy { MediaLiteracyEngine() }
    }
}
