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
import edu.csuci.tweeter.data.FollowException
import edu.csuci.tweeter.data.FollowRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The follower relationship displayed by the Follow button, as far as it's known. */
sealed interface FollowUiState {
    /** A viewer who is logged out or on their own profile, and so can't follow. */
    data object NotAllowed : FollowUiState

    /** Reading the relationship, which isn't known yet. */
    data object Loading : FollowUiState

    /** Reading the relationship failed; the screen says so and offers to read it again. */
    data object ReadFailed : FollowUiState

    /**
     * A known relationship, which the viewer may change.
     *
     * @property isFollowing Whether the viewer follows the displayed user.
     * @property change Where the viewer's last follow or unfollow stands.
     */
    data class Loaded(val isFollowing: Boolean, val change: FollowChange = FollowChange.IDLE) :
        FollowUiState {
        /** Whether the viewer may follow or unfollow now, which they can't while a change runs. */
        val canChange: Boolean
            get() = change != FollowChange.RUNNING
    }
}

/** Where the viewer's last follow or unfollow of the displayed user stands. */
enum class FollowChange {
    /** There was none, or it succeeded. */
    IDLE,

    /** Waiting for the server; the Follow button is disabled meanwhile. */
    RUNNING,

    /** It failed; the screen says so, and the user can try again with the Follow button. */
    FAILED
}

/**
 * The follower relationship and actions for another user's profile.
 *
 * @param userUid The user whose profile is displayed.
 * @param accountRepository Supplies the logged-in user's identity.
 * @param followRepository Reads and changes follower relationships.
 */
class FollowViewModel(
    private val userUid: String,
    private val accountRepository: AccountRepository,
    private val followRepository: FollowRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FollowUiState>(FollowUiState.Loading)
    private var changeJob: Job? = null
    private var relationshipJob: Job? = null

    /** The relationship and loading state displayed by the screen. */
    val uiState: StateFlow<FollowUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.loggedInUid.collectLatest { viewerUid ->
                observeRelationship(viewerUid)
            }
        }
    }

    /** Follows the displayed user, if the viewer doesn't yet and no change is running. */
    fun follow() {
        val state = _uiState.value
        if (state is FollowUiState.Loaded && !state.isFollowing) {
            changeRelationship(state, followRepository::follow)
        }
    }

    /** Unfollows the displayed user, if the viewer follows them and no change is running. */
    fun unfollow() {
        val state = _uiState.value
        if (state is FollowUiState.Loaded && state.isFollowing) {
            changeRelationship(state, followRepository::unfollow)
        }
    }

    /** Reads the relationship again, if reading it failed. */
    fun retry() {
        if (_uiState.value == FollowUiState.ReadFailed) {
            observeRelationship(accountRepository.loggedInUid.value)
        }
    }

    private fun observeRelationship(viewerUid: String?) {
        relationshipJob?.cancel()
        changeJob?.cancel()

        val followerUid = viewerUid?.takeIf { it != userUid }
        if (followerUid == null) {
            _uiState.value = FollowUiState.NotAllowed
            return
        }

        _uiState.value = FollowUiState.Loading
        relationshipJob = viewModelScope.launch {
            readRelationship(followerUid)
        }
    }

    private suspend fun readRelationship(followerUid: String) {
        try {
            followRepository.observeFollowing(followerUid, userUid).collect(::showRelationship)
        } catch (error: FollowException) {
            Log.e("FollowViewModel", "Could not read the follower relationship", error)
            _uiState.value = FollowUiState.ReadFailed
        }
    }

    // Keeps where a change stands: the relationship is read again whenever it changes.
    private fun showRelationship(isFollowing: Boolean) {
        _uiState.update { state ->
            if (state is FollowUiState.Loaded) {
                state.copy(isFollowing = isFollowing)
            } else {
                FollowUiState.Loaded(isFollowing)
            }
        }
    }

    private fun changeRelationship(
        state: FollowUiState.Loaded,
        operation: suspend (String) -> Unit
    ) {
        val changing = state.copy(change = FollowChange.RUNNING)
        if (state.canChange && _uiState.compareAndSet(state, changing)) {
            changeJob = viewModelScope.launch {
                performChange(operation)
            }
        }
    }

    private suspend fun performChange(operation: suspend (String) -> Unit) {
        try {
            operation(userUid)
            finishChange(FollowChange.IDLE)
        } catch (error: FollowException) {
            Log.e("FollowViewModel", "Could not change the follower relationship", error)
            finishChange(FollowChange.FAILED)
        }
    }

    // Reading the relationship may have failed while the change ran; that stays shown.
    private fun finishChange(change: FollowChange) {
        _uiState.update { state ->
            if (state is FollowUiState.Loaded) state.copy(change = change) else state
        }
    }

    companion object {
        /**
         * Creates the ViewModel using the shared account and follow repositories.
         *
         * @param userUid The user whose relationship is displayed.
         */
        fun createFactory(userUid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FollowViewModel(
                    userUid = userUid,
                    accountRepository = application.container.accountRepository,
                    followRepository = application.container.followRepository
                )
            }
        }
    }
}
