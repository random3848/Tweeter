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
 * Items without a destination remain visible but disabled. Connected items use
 * their destination's icons, accessibility label, and navigation callback.
 *
 * @param items The permanent bar items in display order.
 * @param selectedIndex The index in [items] of the displayed destination's item; an index
 *     outside [items], such as -1, selects none.
 * @param onSelect Opens the destination of the item at the given index, which has a route.
 * @param modifier Applied to the whole bar.
 */

@Composable
fun TweeterBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
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
            for (index in items.indices) {
                val item = items[index]
                val enabled = item.route != null
                val selected = enabled && index == selectedIndex
                val icon = if (selected) item.selectedIcon else item.unselectedIcon

                NavigationBarItem(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onSelect(index) },
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
