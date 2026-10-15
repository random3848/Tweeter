package edu.csuci.tweeter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R

/**
 * Home layout with a fixed header and footer surrounding the feed.
 *
 * The caller supplies system-bar padding through [modifier].
 * Future navigation destinations are currently displayed as plain text.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // MainActivity already provides safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { HomeHeader() },
        bottomBar = { HomeFooter() }
    ) { contentPadding ->
        HomeFeed(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    }
}

/** App title and a non-interactive placeholder for the future profile entry. */
@Composable
private fun HomeHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tweeter",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/** Non-interactive icons reserving space for future navigation controls. */
@Composable
private fun HomeFooter() {
    Surface(
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_home),
                    contentDescription = "Home",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Icon(
                    painter = painterResource(R.drawable.ic_add_photo),
                    contentDescription = "Create post",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )

                Icon(
                    painter = painterResource(R.drawable.ic_map),
                    contentDescription = "Map",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Empty feed state until posts are connected to the home screen. */
@Composable
private fun HomeFeed(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No posts to show yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
