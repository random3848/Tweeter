package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileTest {
    @Test
    fun avatar_initialsOfDisplayName() {
        val profile = UserProfile.createDefault("uid").copy(displayName = "Maya Kim")

        assertEquals(Avatar(initials = "MK"), profile.avatar)
    }

    @Test
    fun createDefault_guestWithNothingSet() {
        assertEquals(
            UserProfile(uid = "uid", displayName = "Guest", bio = "", photoUrl = ""),
            UserProfile.createDefault("uid")
        )
    }
}
