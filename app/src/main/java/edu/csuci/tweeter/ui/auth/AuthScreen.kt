package edu.csuci.tweeter.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * Email/password form for logging in or creating an account.
 *
 * Password fields stay in memory and are not saved across screen recreation.
 *
 * @param creatingAccount Whether to show registration instead of login.
 * @param onBack Returns to the previous screen.
 * @param onAuthenticated Returns to Profile after successful authentication.
 * @param modifier Applied to the whole screen.
 * @param authViewModel Submits credentials and supplies loading and error state.
 */
@Composable
fun AuthScreen(
    creatingAccount: Boolean,
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory)
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    val titleId = if (creatingAccount) {
        R.string.auth_create_account
    } else {
        R.string.auth_log_in
    }
    val fieldsEnabled = !authViewModel.isSubmitting && !authViewModel.isAuthenticated
    val errorMessageId = authViewModel.errorMessageId

    LaunchedEffect(authViewModel.isAuthenticated) {
        if (authViewModel.isAuthenticated) {
            password = ""
            confirmation = ""
            currentOnAuthenticated()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        BackTopBar(
            title = stringResource(titleId),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    authViewModel.clearError()
                },
                label = { Text(stringResource(R.string.auth_email)) },
                enabled = fieldsEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    authViewModel.clearError()
                },
                label = { Text(stringResource(R.string.auth_password)) },
                enabled = fieldsEnabled,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (creatingAccount) {
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        authViewModel.clearError()
                    },
                    label = { Text(stringResource(R.string.auth_confirm_password)) },
                    enabled = fieldsEnabled,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessageId != null) {
                Text(
                    text = stringResource(errorMessageId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (creatingAccount) {
                        authViewModel.createAccount(email, password, confirmation)
                    } else {
                        authViewModel.logIn(email, password)
                    }
                },
                enabled = fieldsEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (authViewModel.isSubmitting) {
                            R.string.auth_submitting
                        } else {
                            titleId
                        }
                    )
                )
            }

            if (!creatingAccount) {
                TextButton(
                    onClick = { authViewModel.resetPassword(email) },
                    enabled = fieldsEnabled
                ) {
                    Text(text = stringResource(R.string.auth_forgot_password))
                }

                if (authViewModel.passwordResetSent) {
                    Text(
                        text = stringResource(R.string.auth_reset_email_sent),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
