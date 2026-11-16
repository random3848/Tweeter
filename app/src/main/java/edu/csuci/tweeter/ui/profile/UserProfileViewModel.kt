package edu.csuci.tweeter.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The state of a profile opened by its user's ID. */
sealed interface UserProfileUiState {
    /** The profile and posts are still being read. */
    data object Loading : UserProfileUiState

    /** The profile or posts could not be read. */
    data object Failed : UserProfileUiState

    /**
     * The profile and posts available to display.
     *
     * @property profile The user whose profile was opened.
     * @property posts That user's posts.
     * @property isOwnProfile Whether the viewer owns this profile.
     * @property isLoggedIn Whether the viewer has a registered account session.
     */
    data class Loaded(
        val profile: UserProfile,
        val posts: List<Post>,
        val isOwnProfile: Boolean,
        val isLoggedIn: Boolean
    ) : UserProfileUiState
}

/**
 * The profile and posts of the user whose ID was opened.
 *
 * @param userUid The user whose profile is requested.
 * @param accountRepository Identifies the viewer.
 * @param profileRepository Supplies the requested profile.
 * @param postRepository Supplies that user's posts.
 */
class UserProfileViewModel(
    userUid: String,
    accountRepository: AccountRepository,
    profileRepository: ProfileRepository,
    postRepository: PostRepository
) : ViewModel() {
    /** The requested profile, its posts and the viewer's ownership status. */
    val uiState: StateFlow<UserProfileUiState> =
        combine<UserProfile, List<Post>, String?, UserProfileUiState>(
            profileRepository.getProfile(userUid),
            postRepository.getPostsBy(userUid),
            accountRepository.loggedInUid
        ) { profile, posts, viewerUid ->
            UserProfileUiState.Loaded(
                profile = profile,
                posts = posts,
                isOwnProfile = viewerUid != null && viewerUid == userUid,
                isLoggedIn = viewerUid != null
            )
        }.catch { error ->
            Log.e("UserProfileViewModel", "Could not read the requested profile", error)
            emit(UserProfileUiState.Failed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserProfileUiState.Loading
        )

    companion object {
        /**
         * Creates the ViewModel using the shared repositories.
         *
         * @param userUid The user whose profile should be displayed.
         */
        fun createFactory(userUid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                UserProfileViewModel(
                    userUid = userUid,
                    accountRepository = application.container.accountRepository,
                    profileRepository = application.container.profileRepository,
                    postRepository = application.container.postRepository
                )
            }
        }
    }
}
