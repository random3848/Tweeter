package edu.csuci.tweeter.ui.profile

import edu.csuci.tweeter.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditsTest {
    private val profile = UserProfile(
        uid = "uid",
        displayName = "Maya Kim",
        bio = "Spiders, mostly.",
        photoUrl = "https://example.com/maya.jpg"
    )

    @Test
    fun isValid_nameAndBio_true() {
        assertTrue(ProfileEdits(displayName = "Maya", bio = "Birds.").isValid)
    }

    @Test
    fun isValid_emptyBio_true() {
        assertTrue(ProfileEdits(displayName = "Maya", bio = "").isValid)
    }

    @Test
    fun isValid_blankName_false() {
        assertFalse(ProfileEdits(displayName = "   ", bio = "").isValid)
    }

    @Test
    fun isValid_nameAtLimit_true() {
        val name = "a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH)

        assertTrue(ProfileEdits(displayName = name, bio = "").isValid)
    }

    @Test
    fun isValid_nameOverLimit_false() {
        val name = "a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH + 1)

        assertFalse(ProfileEdits(displayName = name, bio = "").isValid)
    }

    @Test
    fun isValid_nameAtLimitAfterTrimming_true() {
        val name = "  " + "a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH) + "  "

        assertTrue(ProfileEdits(displayName = name, bio = "").isValid)
    }

    @Test
    fun isValid_bioAtLimit_true() {
        val bio = "a".repeat(UserProfile.MAX_BIO_LENGTH)

        assertTrue(ProfileEdits(displayName = "Maya", bio = bio).isValid)
    }

    @Test
    fun isValid_bioOverLimit_false() {
        val bio = "a".repeat(UserProfile.MAX_BIO_LENGTH + 1)

        assertFalse(ProfileEdits(displayName = "Maya", bio = bio).isValid)
    }

    @Test
    fun applyTo_validEdits_replacesTrimmedNameAndBio() {
        val edits = ProfileEdits(displayName = "  Maya Lin ", bio = " Birds, now.\n")

        assertEquals(
            profile.copy(displayName = "Maya Lin", bio = "Birds, now."),
            edits.applyTo(profile)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun applyTo_invalidEdits_throws() {
        ProfileEdits(displayName = "", bio = "").applyTo(profile)
    }

    @Test
    fun of_profile_startsFromItsNameAndBio() {
        assertEquals(
            ProfileEdits(displayName = "Maya Kim", bio = "Spiders, mostly."),
            ProfileEdits.of(profile)
        )
    }
}
