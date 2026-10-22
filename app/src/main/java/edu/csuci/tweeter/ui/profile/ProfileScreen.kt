package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * The logged-in user's profile: their picture, name and bio, then the posts they shared. While
 * nobody is logged in, it asks the user to log in instead.
 *
 * It is opened from Feed and has its own top bar with a back arrow.
 *
 * @param onBack Leaves Profile for the screen it was opened from.
 * @param modifier Applied to the whole screen.
 * @param profileViewModel Supplies the profile and posts, and logs the user in.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory)
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.navigation_profile), onBack = onBack)

        when (val state = uiState) {
            ProfileUiState.Loading -> {
                // Reading the profile takes a moment; show nothing rather than a flash of
                // the log-in prompt or an empty profile.
            }

            ProfileUiState.LoggedOut -> {
                LogInPrompt(
                    isLoggingIn = profileViewModel.isLoggingIn,
                    logInFailed = profileViewModel.logInFailed,
                    onLogIn = profileViewModel::logIn,
                    onCreateAccount = profileViewModel::logIn
                )
            }

            is ProfileUiState.LoggedIn -> {
                ProfileContent(
                    profile = state.profile,
                    posts = state.posts
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(profile: UserProfile, posts: List<Post>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ProfileHeader(
                profile = profile,
                postCount = posts.size,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (posts.isEmpty()) {
            item {
                NoPostsMessage(modifier = Modifier.padding(16.dp))
            }
        } else {
            items(items = posts, key = { post -> post.id }) { post ->
                ProfilePostItem(post = post, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile, postCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(
                profile = profile,
                size = 88.dp,
                initialsStyle = MaterialTheme.typography.headlineMedium
            )
            Column {
                Text(text = profile.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(R.plurals.profile_post_count, postCount, postCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (profile.bio.isNotBlank()) {
            Text(text = profile.bio, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoPostsMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.profile_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.profile_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
