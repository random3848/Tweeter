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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The follower total displayed on a profile.
 *
 * @property count The total, or null before a successful read.
 * @property hasFailed Whether reading the total failed.
 */
data class FollowerCountUiState(val count: Int? = null, val hasFailed: Boolean = false)

/**
 * Observes the follower total for one profile.
 *
 * @param userUid The user whose followers are counted.
 * @param followRepository Supplies live follower totals.
 * @param accountRepository Identifies account changes that restart the count listeners.
 */
class FollowerCountViewModel(
    private val userUid: String,
    private val followRepository: FollowRepository,
    accountRepository: AccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FollowerCountUiState())
    private var observationJob: Job? = null

    private val _followingUiState = MutableStateFlow(FollowerCountUiState())
    private var followingJob: Job? = null

    /** The following total, loading state and read error. */
    val followingUiState = _followingUiState.asStateFlow()

    /** The follower total, loading state and read error. */
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.loggedInUid.collect {
                retry()
                retryFollowing()
            }
        }
    }

    /** Starts reading again, cancelling the previous listener. */
    fun retry() {
        observationJob?.cancel()
        _uiState.value = FollowerCountUiState()
        observationJob = viewModelScope.launch {
            try {
                followRepository.observeFollowerCount(userUid).collect { count ->
                    _uiState.value = FollowerCountUiState(count = count)
                }
            } catch (error: FollowException) {
                Log.e("FollowerCountViewModel", "Could not read follower count", error)
                _uiState.value = FollowerCountUiState(hasFailed = true)
            }
        }
    }

    /** Restarts reading the following total, cancelling the previous listener. */
    fun retryFollowing() {
        followingJob?.cancel()
        _followingUiState.value = FollowerCountUiState()
        followingJob = viewModelScope.launch {
            try {
                followRepository.observeFollowingCount(userUid).collect { count ->
                    _followingUiState.value = FollowerCountUiState(count = count)
                }
            } catch (error: FollowException) {
                Log.e("FollowerCountViewModel", "Could not read following count", error)
                _followingUiState.value = FollowerCountUiState(hasFailed = true)
            }
        }
    }

    companion object {
        /**
         * Creates the ViewModel using the shared follow repository.
         *
         * @param userUid The user whose followers are counted.
         */
        fun createFactory(userUid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FollowerCountViewModel(
                    userUid = userUid,
                    followRepository = application.container.followRepository,
                    accountRepository = application.container.accountRepository
                )
            }
        }
    }
}
