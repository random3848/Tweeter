package edu.csuci.tweeter.ui.profile

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    private val profile = UserProfile(
        uid = "uid",
        displayName = "Maya Kim",
        bio = "Spiders, mostly.",
        photoUrl = ""
    )
    private val profileRepository = FakeProfileRepository(profile)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loggedIn_editsStartFromProfile() {
        val viewModel = createViewModel()

        assertEquals(ProfileEdits.of(profile), viewModel.edits)
        assertFalse(viewModel.isDone)
    }

    @Test
    fun init_nobodyLoggedIn_isDone() {
        val viewModel = createViewModel(loggedInUid = null)

        assertNull(viewModel.edits)
        assertTrue(viewModel.isDone)
    }

    @Test
    fun init_profileCantBeRead_isDone() {
        profileRepository.failsToRead = true

        val viewModel = createViewModel()

        assertTrue(viewModel.isDone)
    }

    @Test
    fun init_savedEdits_restoresThem() {
        val savedStateHandle = SavedStateHandle()
        createViewModel(savedStateHandle = savedStateHandle).updateDisplayName("Maya Lin")

        val restored = createViewModel(savedStateHandle = savedStateHandle)

        assertEquals("Maya Lin", restored.edits?.displayName)
    }

    @Test
    fun updateDisplayName_atLimit_changesEdits() {
        val viewModel = createViewModel()
        val name = "a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH)

        viewModel.updateDisplayName(name)

        assertEquals(name, viewModel.edits?.displayName)
    }

    @Test
    fun updateDisplayName_overLimit_ignored() {
        val viewModel = createViewModel()

        viewModel.updateDisplayName("a".repeat(UserProfile.MAX_DISPLAY_NAME_LENGTH + 1))

        assertEquals(profile.displayName, viewModel.edits?.displayName)
    }

    @Test
    fun updateBio_atLimit_changesEdits() {
        val viewModel = createViewModel()
        val bio = "a".repeat(UserProfile.MAX_BIO_LENGTH)

        viewModel.updateBio(bio)

        assertEquals(bio, viewModel.edits?.bio)
    }

    @Test
    fun updateBio_overLimit_ignored() {
        val viewModel = createViewModel()

        viewModel.updateBio("a".repeat(UserProfile.MAX_BIO_LENGTH + 1))

        assertEquals(profile.bio, viewModel.edits?.bio)
    }

    @Test
    fun canSave_blankName_false() {
        val viewModel = createViewModel()

        viewModel.updateDisplayName("  ")

        assertFalse(viewModel.canSave)
    }

    @Test
    fun save_validEdits_savesTrimmedProfileAndIsDone() {
        val viewModel = createViewModel()
        viewModel.updateDisplayName(" Maya Lin ")
        viewModel.updateBio("Birds, now. ")

        viewModel.save()

        assertEquals(
            profile.copy(displayName = "Maya Lin", bio = "Birds, now."),
            profileRepository.savedProfile
        )
        assertTrue(viewModel.isDone)
    }

    @Test
    fun save_blankName_savesNothing() {
        val viewModel = createViewModel()
        viewModel.updateDisplayName("")

        viewModel.save()

        assertNull(profileRepository.savedProfile)
        assertFalse(viewModel.isDone)
    }

    @Test
    fun save_repositoryFails_reportsFailureAndStaysOpen() {
        profileRepository.failsToSave = true
        val viewModel = createViewModel()

        viewModel.save()

        assertTrue(viewModel.saveFailed)
        assertFalse(viewModel.isSaving)
        assertFalse(viewModel.isDone)
    }

    private fun createViewModel(
        loggedInUid: String? = profile.uid,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): EditProfileViewModel = EditProfileViewModel(
        profileRepository = profileRepository,
        accountRepository = FakeAccountRepository(loggedInUid),
        savedStateHandle = savedStateHandle
    )

    private class FakeProfileRepository(private val profile: UserProfile) : ProfileRepository {
        var failsToRead = false
        var failsToSave = false
        var savedProfile: UserProfile? = null

        override fun getProfile(uid: String): Flow<UserProfile> = if (failsToRead) {
            flow { throw FirebaseException("Could not read the profile") }
        } else {
            flowOf(profile)
        }

        override suspend fun saveProfile(profile: UserProfile) {
            if (failsToSave) {
                throw FirebaseException("Could not save the profile")
            }
            savedProfile = profile
        }
    }

    private class FakeAccountRepository(uid: String?) : AccountRepository {
        override val loggedInUid: StateFlow<String?> = MutableStateFlow(uid)

        override suspend fun logIn(email: String, password: String) = Unit

        override suspend fun createAccount(email: String, password: String) = Unit

        override suspend fun logOut() = Unit

        override suspend fun resetPassword(email: String) = Unit
    }
}
