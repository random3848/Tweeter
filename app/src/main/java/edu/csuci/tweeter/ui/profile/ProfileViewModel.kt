package edu.csuci.tweeter.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import java.util.Objects
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What [ProfileScreen] shows. */
sealed interface ProfileUiState {
    /** Still reading who is logged in and their profile. */
    data object Loading : ProfileUiState

    /** Nobody is logged in, so the screen asks the user to log in. */
    data object LoggedOut : ProfileUiState

    /**
     * The logged-in user's profile.
     *
     * Equal to another [LoggedIn] with the same profile, the same table and the same rows, so the
     * state flow tells new state from old without comparing the posts in its tables.
     *
     * @property profile What they show about themselves.
     * @property table Every post, newest first.
     * @property rows The rows of [table] holding the posts they shared, newest first.
     */
    class LoggedIn(val profile: UserProfile, val table: PostTable, val rows: IntArray) :
        ProfileUiState {
        override fun equals(other: Any?): Boolean = other is LoggedIn &&
            profile == other.profile &&
            table === other.table &&
            rows.contentEquals(other.rows)

        override fun hashCode(): Int = Objects.hash(profile, table, rows.contentHashCode())

        override fun toString(): String =
            "LoggedIn(profile=$profile, table=$table, rows=${rows.contentToString()})"
    }
}

/**
 * The registered user's profile and posts for ProfileScreen.
 *
 * Observes account changes so successful authentication updates Profile.
 *
 * @param accountRepository Supplies the registered user's identity.
 * @param profileRepository Where the profile is read from.
 * @param postRepository Where every post is read from; Profile shows the user's rows of it.
 * @param rowsDispatcher Where the user's rows are found: off the main thread, since finding them
 *     is a pass over every post.
 * @param sharing When [uiState] follows the account, profile and posts: while Profile
 *     collects it, in the app.
 */
class ProfileViewModel(
    private val accountRepository: AccountRepository,
    profileRepository: ProfileRepository,
    private val postRepository: PostRepository,
    private val rowsDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    /** The current state of the screen, updated as the user logs in and the profile changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> = accountRepository.loggedInUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(ProfileUiState.LoggedOut)
            } else {
                combine(profileRepository.getProfile(uid), postsBy(uid)) { profile, posts ->
                    ProfileUiState.LoggedIn(profile, posts.table, posts.rows)
                }.catch { error ->
                    // Keep showing the last profile.
                    Log.e("ProfileViewModel", "Could not read the profile", error)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = sharing,
            initialValue = ProfileUiState.Loading
        )

    // Found once per table, which the repository sends only when the posts change.
    private fun postsBy(uid: String): Flow<UserPosts> = postRepository.getPostTable()
        .map { table -> UserPosts(table, table.rowsBy(uid)) }
        .flowOn(rowsDispatcher)

    /** Signs out and restores the Firebase session used for guest browsing. */
    fun logOut() {
        viewModelScope.launch {
            try {
                accountRepository.logOut()
            } catch (error: FirebaseException) {
                Log.e(
                    "ProfileViewModel",
                    "Signed out, but could not restore guest access",
                    error
                )
            }
        }
    }

    companion object {
        /** Creates a [ProfileViewModel] that reads the app's shared repositories. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                ProfileViewModel(
                    accountRepository = application.container.accountRepository,
                    profileRepository = application.container.profileRepository,
                    postRepository = application.container.postRepository,
                    rowsDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}

// The rows of [table] holding one user's posts.
private class UserPosts(val table: PostTable, val rows: IntArray)
