package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R

/**
 * What Profile shows while nobody is logged in: a prompt to log in or create an account.
 *
 * Until create-account/login is built, Profile passes the placeholder log-in to both buttons;
 * that work points them at its own screens.
 *
 * @param isLoggingIn Whether a log-in is running, which disables both buttons.
 * @param logInFailed Whether the latest log-in failed, which shows an error.
 * @param onLogIn Logs the user in.
 * @param onCreateAccount Creates an account for the user.
 * @param modifier Applied to the whole prompt.
 */
@Composable
fun LogInPrompt(
    isLoggingIn: Boolean,
    logInFailed: Boolean,
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.profile_log_in_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.profile_log_in_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onLogIn,
            enabled = !isLoggingIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.profile_log_in))
        }
        OutlinedButton(
            onClick = onCreateAccount,
            enabled = !isLoggingIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.profile_create_account))
        }
        if (logInFailed) {
            Text(
                text = stringResource(R.string.profile_log_in_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
