package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.UserProfile

/**
 * A user's round profile picture: their photo, or their initials on gray when they have none.
 *
 * @param profile Whose picture to show.
 * @param size The circle's diameter.
 * @param initialsStyle The style of the initials, sized to fit [size].
 * @param modifier Applied to the circle.
 */
@Composable
fun ProfileAvatar(
    profile: UserProfile,
    size: Dp,
    initialsStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (profile.photoUrl.isBlank()) {
            Text(
                text = profile.avatar.initials,
                style = initialsStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            AsyncImage(
                model = profile.photoUrl,
                contentDescription = stringResource(R.string.profile_photo_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
