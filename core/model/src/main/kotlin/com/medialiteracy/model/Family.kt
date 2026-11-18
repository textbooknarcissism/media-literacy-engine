package com.medialiteracy.model

/**
 * The nine techniques grouped into four things that feel different to read.
 *
 * Nine icons is too many to become instinctive; four is learnable in about a
 * week of normal use. The precise technique is still named when the reader taps
 * the headline: the icon only has to say which *kind* of thing is going on.
 *
 * The grouping is by what the headline is doing to you, not by how the detector
 * works internally.
 */
enum class Family(
    val id: String,
    val displayName: String,
    /** One line, used wherever the family itself needs explaining. */
    val explanation: String,
) {
    /** Big emotion where plain words would have done the job. */
    SENSATIONALISM(
        "sensationalism",
        "Strong feelings",
        "Big emotion where plain words would have done the job.",
    ),

    /** Things that might happen, or people you cannot go and check. */
    SPECULATION(
        "speculation_sourcing",
        "Guesswork and sourcing",
        "Things that might happen, or people you cannot go and check.",
    ),

    /** Shaped to pull a tap rather than to tell you what happened. */
    ENGAGEMENT(
        "engagement_hooks",
        "Made to make you click",
        "Shaped to pull a tap rather than to tell you what happened.",
    ),

    /** The writer's own judgment, put as though it were an event. */
    EDITORIALIZING(
        "editorializing",
        "Opinion written as news",
        "The writer's own judgement, put as though it were an event.",
    );

    companion object {
        fun of(category: Category): Family = when (category) {
            Category.FEAR, Category.RAGE -> SENSATIONALISM
            Category.HEDGE, Category.SOURCING, Category.STUDY, Category.OVERHYPE -> SPECULATION
            Category.CURIOSITY, Category.URGENCY -> ENGAGEMENT
            Category.OPINION -> EDITORIALIZING
        }
    }
}
