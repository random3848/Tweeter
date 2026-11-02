package edu.csuci.tweeter.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
 * Items without a screen remain visible but disabled. The others use their icons, accessibility
 * label, and selection callback.
 *
 * @param items The permanent bar items in display order.
 * @param isSelected Whether an item's screen is the one shown.
 * @param onSelect Called with the item the user taps; only items with a screen can be tapped.
 * @param modifier Applied to the whole bar.
 */

@Composable
fun TweeterBottomBar(
    items: List<BottomBarItem>,
    isSelected: (BottomBarItem) -> Boolean,
    onSelect: (BottomBarItem) -> Unit,
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
                val enabled = item.screen != null
                val selected = enabled && isSelected(item)
                val icon = if (selected) item.selectedIcon else item.unselectedIcon

                NavigationBarItem(
                    selected = selected,
                    enabled = enabled,
                    onClick = {
                        if (enabled) {
                            onSelect(item)
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = stringResource(item.description),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(65.dp)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
