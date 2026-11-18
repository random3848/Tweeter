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

/**
 * The follower relationship displayed by the Follow button.
 *
 * @property isFollowing Whether the viewer follows the displayed user.
 * @property canFollow Whether the viewer is logged in and viewing someone else.
 * @property isLoading Whether the relationship is still being read.
 * @property isChanging Whether a follow or unfollow request is running.
 * @property hasFailed Whether reading or changing the relationship failed.
 */
data class FollowUiState(
    val isFollowing: Boolean = false,
    val canFollow: Boolean = false,
    val isLoading: Boolean = false,
    val isChanging: Boolean = false,
    val hasFailed: Boolean = false
)

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
    private val _uiState = MutableStateFlow(FollowUiState())
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

    /** Follows the displayed user when the viewer is allowed to do so. */
    fun follow() {
        if (uiState.value.isFollowing) {
            return
        }
        changeRelationship(followRepository::follow)
    }

    /** Unfollows the displayed user when a following relationship exists. */
    fun unfollow() {
        if (!uiState.value.isFollowing) {
            return
        }
        changeRelationship(followRepository::unfollow)
    }

    /** Retries reading the relationship after a failed attempt. */
    fun retry() {
        val state = uiState.value
        val viewerUid = accountRepository.loggedInUid.value
        if (!state.hasFailed || state.canFollow ||
            viewerUid == null || viewerUid == userUid
        ) {
            return
        }

        observeRelationship(viewerUid)
    }

    private fun observeRelationship(viewerUid: String?) {
        relationshipJob?.cancel()
        changeJob?.cancel()

        val canFollow = viewerUid != null && viewerUid != userUid
        _uiState.value = FollowUiState(
            canFollow = canFollow,
            isLoading = canFollow
        )

        if (viewerUid != null && canFollow) {
            relationshipJob = viewModelScope.launch {
                readRelationship(viewerUid)
            }
        }
    }

    private suspend fun readRelationship(viewerUid: String) {
        try {
            followRepository.observeFollowing(viewerUid, userUid).collect { following ->
                _uiState.update {
                    it.copy(
                        isFollowing = following,
                        isLoading = false
                    )
                }
            }
        } catch (error: FollowException) {
            Log.e("FollowViewModel", "Could not read the follower relationship", error)
            _uiState.update {
                it.copy(
                    canFollow = false,
                    isLoading = false,
                    hasFailed = true
                )
            }
        }
    }

    private fun changeRelationship(operation: suspend (String) -> Unit) {
        val state = uiState.value
        val viewerUid = accountRepository.loggedInUid.value
        if (!state.canFollow || state.isLoading || state.isChanging ||
            viewerUid == null || viewerUid == userUid
        ) {
            return
        }

        _uiState.update { it.copy(isChanging = true, hasFailed = false) }
        changeJob = viewModelScope.launch {
            performChange(operation)
        }
    }

    private suspend fun performChange(operation: suspend (String) -> Unit) {
        try {
            operation(userUid)
        } catch (error: FollowException) {
            Log.e("FollowViewModel", "Could not change the follower relationship", error)
            _uiState.update { it.copy(hasFailed = true) }
        } finally {
            _uiState.update { it.copy(isChanging = false) }
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
