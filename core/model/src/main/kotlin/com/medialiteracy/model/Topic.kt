package com.medialiteracy.model

/**
 * Editorial topic of a news story or feed.
 *
 * Distinct from manipulation [Category]: this is what a story is about (politics, sport,
 * entertainment...), while [Category] is how its headline is worded (fear, rage, curiosity...).
 *
 * Kept concise and clear so filtering stays fast and intuitive.
 */
enum class Topic(val displayName: String) {
    GENERAL("General"),
    POLITICS("Politics"),
    BUSINESS("Business"),
    SPORT("Sport"),
    SCIENCE_TECH("Science & tech"),
    ENTERTAINMENT("Entertainment"),
}
