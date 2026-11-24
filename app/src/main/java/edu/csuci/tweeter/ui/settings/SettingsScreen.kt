package edu.csuci.tweeter.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.navigation.BackTopBar
import edu.csuci.tweeter.ui.theme.BLUE
import edu.csuci.tweeter.ui.theme.ThemeChoice
import edu.csuci.tweeter.ui.theme.WHITE

/**
 * Account settings and preferences, arranged in labelled sections.
 *
 * @param onBack Returns to the previous screen.
 * @param onLoggedOut Opens Profile after logout.
 * @param modifier Applied to the whole screen.
 * @param settingsViewModel Supplies account actions and their status.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory)
) {
    val status by settingsViewModel.status.collectAsStateWithLifecycle()
    val themeChoice by settingsViewModel.themeChoice.collectAsStateWithLifecycle()
    val currentOnLoggedOut by rememberUpdatedState(onLoggedOut)

    LaunchedEffect(status) {
        if (status == SettingsStatus.LOGGED_OUT) {
            currentOnLoggedOut()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        BackTopBar(
            title = stringResource(R.string.settings_title),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 24.dp,
                    end = 20.dp,
                    bottom = 8.dp
                )
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                ThemeSelector(
                    themeChoice = themeChoice,
                    onSelect = settingsViewModel::setThemeChoice,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            Text(
                text = stringResource(R.string.settings_account),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 24.dp,
                    end = 20.dp,
                    bottom = 8.dp
                )
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                TextButton(
                    onClick = settingsViewModel::logOut,
                    enabled = status == SettingsStatus.READY ||
                        status == SettingsStatus.FAILED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (status == SettingsStatus.LOGGING_OUT) {
                                R.string.settings_logging_out
                            } else {
                                R.string.profile_log_out
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }

            if (status == SettingsStatus.FAILED) {
                Text(
                    text = stringResource(R.string.settings_log_out_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(
    themeChoice: ThemeChoice,
    onSelect: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (choice in ThemeChoice.entries) {
            val selectedContainerColor = if (choice == ThemeChoice.LIGHT) {
                BLUE
            } else {
                MaterialTheme.colorScheme.primary
            }
            val selectedLabelColor = if (choice == ThemeChoice.LIGHT) {
                WHITE
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
            FilterChip(
                selected = themeChoice == choice,
                onClick = { onSelect(choice) },
                label = { Text(stringResource(choice.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = selectedContainerColor,
                    selectedLabelColor = selectedLabelColor,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
