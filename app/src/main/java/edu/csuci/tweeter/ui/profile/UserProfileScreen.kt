package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.navigation.BackTopBar
import edu.csuci.tweeter.ui.report.ReportUserButton

/**
 * A profile opened by selecting a user.
 *
 * The owner sees their existing profile screen and account controls.
 * Other viewers see the shared profile layout with following controls and a button that reports
 * the user.
 *
 * @param userUid The user whose profile was opened.
 * @param onBack Returns to the previous screen.
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens registration when the owner screen needs it.
 * @param onEditProfile Opens the viewer's own profile editor.
 * @param onOpenSavedPosts Opens the posts the viewer saved, from their own profile.
 * @param modifier Applied to the whole screen.
 * @param userProfileViewModel Supplies the requested profile and ownership status.
 */
@Composable
fun UserProfileScreen(
    userUid: String,
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSavedPosts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    userProfileViewModel: UserProfileViewModel = viewModel(
        factory = UserProfileViewModel.createFactory(userUid)
    )
) {
    val uiState by userProfileViewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState

    if (state is UserProfileUiState.Loaded && state.isOwnProfile) {
        ProfileScreen(
            onBack = onBack,
            onLogIn = onLogIn,
            onCreateAccount = onCreateAccount,
            onEditProfile = onEditProfile,
            onOpenSavedPosts = onOpenSavedPosts,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(
            title = stringResource(R.string.navigation_profile),
            onBack = onBack
        )

        when (state) {
            UserProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            UserProfileUiState.Failed -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.profile_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is UserProfileUiState.Loaded -> {
                ProfileContent(
                    profile = state.profile,
                    table = state.table,
                    rows = state.rows
                ) {
                    ProfileFollowControls(
                        userUid = userUid,
                        isLoggedIn = state.isLoggedIn,
                        onLogIn = onLogIn
                    )
                    ReportUserButton(
                        userUid = userUid,
                        isLoggedIn = state.isLoggedIn,
                        onLogIn = onLogIn
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileFollowControls(
    userUid: String,
    isLoggedIn: Boolean,
    onLogIn: () -> Unit,
    followViewModel: FollowViewModel = viewModel(
        factory = FollowViewModel.createFactory(userUid)
    )
) {
    val state by followViewModel.uiState.collectAsStateWithLifecycle()

    if (!isLoggedIn) {
        FilledTonalButton(
            onClick = onLogIn,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Text(text = stringResource(R.string.profile_log_in_to_follow))
        }
        return
    }

    FilledTonalButton(
        onClick = {
            if (state.isFollowing) {
                followViewModel.unfollow()
            } else {
                followViewModel.follow()
            }
        },
        enabled = state.canFollow && !state.isLoading && !state.isChanging,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Text(
            text = stringResource(
                if (state.isFollowing) {
                    R.string.profile_unfollow
                } else {
                    R.string.profile_follow
                }
            )
        )
    }

    if (state.hasFailed) {
        Text(
            text = stringResource(R.string.profile_follow_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        if (!state.canFollow) {
            TextButton(onClick = followViewModel::retry) {
                Text(text = stringResource(R.string.profile_follow_retry))
            }
        }
    }
}
