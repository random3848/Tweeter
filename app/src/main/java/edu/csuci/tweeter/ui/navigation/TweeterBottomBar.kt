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
 * Shared bottom navigation displaying permanent items in the supplied order.
 *
 * Items without a destination remain visible but disabled. Connected items use
 * their destination's icons, accessibility label, and navigation callback.
 *
 * @param items The permanent bar items in display order.
 * @param isSelected Whether a connected destination is currently displayed.
 * @param onSelect Opens the selected working destination.
 * @param modifier Applied to the whole bar.
 */

@Composable
fun TweeterBottomBar(
    items: List<BottomBarItem>,
    isSelected: (BottomBarItem) -> Boolean,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                val route = item.route
                val selected = route != null && isSelected(item)
                val icon = if (selected) item.selectedIcon else item.unselectedIcon

                NavigationBarItem(
                    selected = selected,
                    enabled = route != null,
                    onClick = {
                        if (route != null) {
                            onSelect(route)
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = stringResource(item.description)
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
