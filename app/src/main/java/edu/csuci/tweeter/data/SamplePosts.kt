package edu.csuci.tweeter.data

import java.time.Duration
import java.time.Instant

/**
 * Posts that [FakePostRepository] starts with, newest first, so Feed and Map have something to
 * show before Post works.
 *
 * Timestamps are relative to when the app starts, so they always read as recent.
 */
val SAMPLE_POSTS: List<Post> = listOf(
    Post(
        id = "sample-1",
        username = "maya.k",
        avatar = Avatar(initials = "MK"),
        locationName = "Library, oak tree",
        latitude = 34.16294,
        longitude = -119.04431,
        imageUri = sampleImageUri("sample_spider"),
        caption = "Orb weaver waiting in its web outside the library.",
        likeCount = 24,
        commentCount = 3,
        isLiked = false,
        isBookmarked = false,
        timestamp = minutesAgo(12)
    ),
    Post(
        id = "sample-2",
        username = "jordan.r",
        avatar = Avatar(initials = "JR"),
        locationName = "Santa Rosa Island",
        latitude = 33.96000,
        longitude = -120.08000,
        imageUri = sampleImageUri("sample_island_fox"),
        caption = "Island fox! Only found on the Channel Islands.",
        likeCount = 8,
        commentCount = 0,
        isLiked = true,
        isBookmarked = false,
        timestamp = minutesAgo(95)
    ),
    Post(
        id = "sample-3",
        username = "sam.t",
        avatar = Avatar(initials = "ST"),
        locationName = "Calleguas Creek",
        latitude = 34.17500,
        longitude = -119.06000,
        imageUri = sampleImageUri("sample_otter"),
        caption = "Otter drying off on the riverbank.",
        likeCount = 41,
        commentCount = 7,
        isLiked = false,
        isBookmarked = true,
        timestamp = minutesAgo(60 * 26)
    )
)

/** A URI for the photo [name] in `res/drawable`. */
private fun sampleImageUri(name: String) = "android.resource://edu.csuci.tweeter/drawable/$name"

/** The moment [minutes] minutes before now. */
private fun minutesAgo(minutes: Long) = Instant.now().minus(Duration.ofMinutes(minutes))
