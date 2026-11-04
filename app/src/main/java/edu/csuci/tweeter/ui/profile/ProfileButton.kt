package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R

/**
 * The round button on Feed's top bar that opens Profile. It shows the logged-in user's picture,
 * or a person icon while nobody is logged in.
 *
 * @param onClick Opens Profile.
 * @param modifier Applied to the button.
 * @param profileButtonViewModel Supplies whose picture to show.
 */
@Composable
fun ProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    profileButtonViewModel: ProfileButtonViewModel = viewModel(
        factory = ProfileButtonViewModel.factory
    )
) {
    val profile by profileButtonViewModel.profile.collectAsStateWithLifecycle()
    val description = stringResource(R.string.navigation_profile)

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = description }
    ) {
        val loggedInProfile = profile
        if (loggedInProfile != null) {
            ProfileAvatar(
                profile = loggedInProfile,
                size = 36.dp,
                initialsStyle = MaterialTheme.typography.labelLarge,
                // The button's description says what it does; the initials would only repeat it.
                modifier = Modifier.clearAndSetSemantics {}
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
