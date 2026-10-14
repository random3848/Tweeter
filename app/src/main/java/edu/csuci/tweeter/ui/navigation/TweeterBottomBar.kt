package edu.csuci.tweeter.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The bar at the bottom of every screen: one icon-only tab per destination, on white with a thin
 * gray line above it. The selected tab shows its filled icon and the others their outlined one;
 * there is no highlight pill behind the selected tab.
 *
 * @param destinations The tabs, in order.
 * @param isSelected Whether a destination is the one on screen.
 * @param onSelect Called with the tab the user tapped.
 * @param modifier Applied to the whole bar.
 */
@Composable
fun TweeterBottomBar(
    destinations: List<TopLevelDestination>,
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { destination ->
                val selected = isSelected(destination)
                val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = stringResource(destination.contentDescription)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
