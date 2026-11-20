package edu.csuci.tweeter.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
 * Email/password form for logging in, which can also send a password reset link.
 *
 * The password stays in memory and is not saved across screen recreation.
 *
 * @param onBack Returns to the previous screen.
 * @param onAuthenticated Returns to Profile after successful login.
 * @param modifier Applied to the whole screen.
 * @param authViewModel Submits credentials and supplies the form's status.
 */
@Composable
fun LogInScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory)
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val status = authViewModel.status

    AuthenticatedEffect(status) {
        password = ""
        onAuthenticated()
    }

    AuthForm(titleId = R.string.auth_log_in, onBack = onBack, modifier = modifier) {
        EmailField(
            email = email,
            onEmailChange = {
                email = it
                authViewModel.clearError()
            },
            enabled = status.acceptsInput
        )
        PasswordField(
            labelId = R.string.auth_password,
            password = password,
            onPasswordChange = {
                password = it
                authViewModel.clearError()
            },
            enabled = status.acceptsInput
        )
        ErrorMessage(status)
        SubmitButton(
            labelId = R.string.auth_log_in,
            status = status,
            onClick = { authViewModel.logIn(email, password) }
        )
        TextButton(
            onClick = { authViewModel.resetPassword(email) },
            enabled = status.acceptsInput
        ) {
            Text(text = stringResource(R.string.auth_forgot_password))
        }

        if (status == AuthStatus.ResetSent) {
            Text(
                text = stringResource(R.string.auth_reset_email_sent),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Email/password form for creating an account, asking for the password twice.
 *
 * The passwords stay in memory and are not saved across screen recreation.
 *
 * @param onBack Returns to the previous screen.
 * @param onAuthenticated Returns to Profile after successful registration.
 * @param modifier Applied to the whole screen.
 * @param authViewModel Submits credentials and supplies the form's status.
 */
@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory)
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val status = authViewModel.status

    AuthenticatedEffect(status) {
        password = ""
        confirmation = ""
        onAuthenticated()
    }

    AuthForm(titleId = R.string.auth_create_account, onBack = onBack, modifier = modifier) {
        EmailField(
            email = email,
            onEmailChange = {
                email = it
                authViewModel.clearError()
            },
            enabled = status.acceptsInput
        )
        PasswordField(
            labelId = R.string.auth_password,
            password = password,
            onPasswordChange = {
                password = it
                authViewModel.clearError()
            },
            enabled = status.acceptsInput
        )
        PasswordField(
            labelId = R.string.auth_confirm_password,
            password = confirmation,
            onPasswordChange = {
                confirmation = it
                authViewModel.clearError()
            },
            enabled = status.acceptsInput
        )
        ErrorMessage(status)
        SubmitButton(
            labelId = R.string.auth_create_account,
            status = status,
            onClick = { authViewModel.createAccount(email, password, confirmation) }
        )
    }
}

/** Calls [onAuthenticated] once [status] becomes [AuthStatus.Authenticated]. */
@Composable
private fun AuthenticatedEffect(status: AuthStatus, onAuthenticated: () -> Unit) {
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    val isAuthenticated = status == AuthStatus.Authenticated

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            currentOnAuthenticated()
        }
    }
}

/** The screen titled [titleId] with a back button, with [content] in a scrolling column. */
@Composable
private fun AuthForm(
    @StringRes titleId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun EmailField(email: String, onEmailChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.auth_email)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(
    @StringRes labelId: Int,
    password: String,
    onPasswordChange: (String) -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(labelId)) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** The error [status] holds, if any. */
@Composable
private fun ErrorMessage(status: AuthStatus) {
    val errorMessageId = (status as? AuthStatus.Editing)?.errorMessageId ?: return

    Text(
        text = stringResource(errorMessageId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

/** The form's main button, labelled [labelId], or "Please wait…" while a request runs. */
@Composable
private fun SubmitButton(@StringRes labelId: Int, status: AuthStatus, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = status.acceptsInput,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                if (status == AuthStatus.Submitting) R.string.auth_submitting else labelId
            )
        )
    }
}
