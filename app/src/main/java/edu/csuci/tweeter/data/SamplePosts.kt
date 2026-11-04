package edu.csuci.tweeter.data

import java.time.Duration
import java.time.Instant

/**
 * Posts that [FakePostRepository] starts with, newest first, so Feed and Map have something to
 * show before Post works.
 *
 * They have no photos (`imageUri` is empty), so screens show their placeholder for a missing
 * photo. Timestamps are relative to when the app starts, so they always read as recent.
 *
 * Add or change sample posts here freely; nothing depends on their exact contents.
 */
val SAMPLE_POSTS: List<Post> = listOf(
    Post(
        id = "sample-1",
        authorUid = "",
        username = "maya.k",
        avatar = Avatar(initials = "MK"),
        locationName = "Library, oak tree",
        latitude = 34.16294,
        longitude = -119.04431,
        imageUri = "",
        caption = "Orb weaver waiting in its web outside the library.",
        likeCount = 24,
        commentCount = 3,
        isLiked = false,
        isBookmarked = false,
        timestamp = subtractFromNow(12)
    ),
    Post(
        id = "sample-2",
        authorUid = "",
        username = "jordan.r",
        avatar = Avatar(initials = "JR"),
        locationName = "Santa Rosa Island",
        latitude = 33.96000,
        longitude = -120.08000,
        imageUri = "",
        caption = "Island fox! Only found on the Channel Islands.",
        likeCount = 8,
        commentCount = 0,
        isLiked = true,
        isBookmarked = false,
        timestamp = subtractFromNow(95)
    ),
    Post(
        id = "sample-3",
        authorUid = "",
        username = "sam.t",
        avatar = Avatar(initials = "ST"),
        locationName = "Calleguas Creek",
        latitude = 34.17500,
        longitude = -119.06000,
        imageUri = "",
        caption = "Otter drying off on the riverbank.",
        likeCount = 41,
        commentCount = 7,
        isLiked = false,
        isBookmarked = true,
        timestamp = subtractFromNow(60 * 26)
    )
)

/** Subtracts [minutes] minutes from the current time. */
private fun subtractFromNow(minutes: Long) = Instant.now().minus(Duration.ofMinutes(minutes))
