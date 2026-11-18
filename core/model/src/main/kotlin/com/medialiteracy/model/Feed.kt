package com.medialiteracy.model

import java.time.Instant

/** One article as the feed describes it. Nothing here has been fetched or analyzed yet. */
data class FeedItem(
    val title: String,
    val link: String?,
    val publishedAt: Instant?,
    /** The short blurb the feed provides. Often empty, often the whole article. Never trusted. */
    val summary: String?,
    /** The feed's own id for this item, used to tell new articles from ones already seen. */
    val id: String?,
    /** A small preview picture the feed offers for this article, if any. */
    val imageUrl: String? = null,
    /**
     * The larger picture behind [imageUrl], if the feed offered one - e.g.
     * <media:content> alongside a smaller <media:thumbnail>. Null when the
     * feed only ever gave one size, in which case [imageUrl] is used for both.
     */
    val fullImageUrl: String? = null,
    /** Section, category, or tag strings extracted from the feed's item metadata. */
    val categories: List<String> = emptyList(),
)

/** A feed as published by a site, straight after parsing. */
data class ParsedFeed(
    val title: String?,
    val siteLink: String?,
    val items: List<FeedItem>,
)
