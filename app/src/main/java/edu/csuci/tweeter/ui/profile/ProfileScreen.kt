package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.auth.LogInPrompt
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * The logged-in user's profile: their picture, name and bio, then the posts they shared, each with
 * a delete button that asks before deleting. Under the bio, buttons open Edit profile and the
 * posts the user saved. While nobody is logged in, it asks the user to log in instead.
 *
 * It is opened from Feed and has its own top bar with a back arrow.
 *
 * @param onBack Leaves Profile for the screen it was opened from.
 * @param onLogIn Opens the login form.
 * @param onCreateAccount Opens the registration form.
 * @param onEditProfile Opens Edit profile.
 * @param onOpenSavedPosts Opens the posts the user saved.
 * @param onOpenSettings Opens account settings.
 * @param modifier Applied to the whole screen.
 * @param profileViewModel Supplies the registered user's profile and posts.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSavedPosts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory)
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(
            title = stringResource(R.string.navigation_profile),
            onBack = onBack,
            actions = {
                if (uiState is ProfileUiState.LoggedIn) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                }
            }
        )

        when (val state = uiState) {
            ProfileUiState.Loading -> {
                // Reading the profile takes a moment; show nothing rather than a flash of
                // the log-in prompt or an empty profile.
            }

            ProfileUiState.LoggedOut -> {
                LogInPrompt(
                    title = stringResource(R.string.profile_log_in_title),
                    onLogIn = onLogIn,
                    onCreateAccount = onCreateAccount
                )
            }

            is ProfileUiState.LoggedIn -> {
                ProfileContent(
                    profile = state.profile,
                    table = state.table,
                    rows = state.rows,
                    onDeletePost = profileViewModel::deletePost
                ) {
                    ProfileActions(
                        onEditProfile = onEditProfile,
                        onOpenSavedPosts = onOpenSavedPosts
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActions(onEditProfile: () -> Unit, onOpenSavedPosts: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onEditProfile,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) {
            Text(text = stringResource(R.string.profile_edit))
        }
        OutlinedButton(
            onClick = onOpenSavedPosts,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) {
            Text(text = stringResource(R.string.profile_saved_posts))
        }
    }
}
