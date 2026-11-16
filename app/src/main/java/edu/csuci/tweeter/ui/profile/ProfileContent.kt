package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.ui.postDetailsText

/**
 * The shared profile layout: avatar, name, bio, actions and posts.
 *
 * @param profile The user whose profile is displayed.
 * @param table The table containing the posts.
 * @param rows The user's post rows, in display order.
 * @param modifier Applied to the scrolling content.
 * @param actions Controls displayed below the bio.
 */
@Composable
internal fun ProfileContent(
    profile: UserProfile,
    table: PostTable,
    rows: IntArray,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ProfileHeader(
                profile = profile,
                postCount = rows.size,
                modifier = Modifier.padding(horizontal = 16.dp),
                actions = actions
            )
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (rows.isEmpty()) {
            item {
                NoPostsMessage(modifier = Modifier.padding(16.dp))
            }
        } else {
            items(count = rows.size, key = { i -> table.id(rows[i]) }) { i ->
                val row = rows[i]
                ProfilePostItem(
                    imageUri = table.imageUri(row),
                    caption = table.caption(row),
                    details = postDetailsText(table.locationName(row), table.timestampMillis(row)),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    postCount: Int,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
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
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_post_count,
                        postCount,
                        postCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (profile.bio.isNotBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        actions()
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
