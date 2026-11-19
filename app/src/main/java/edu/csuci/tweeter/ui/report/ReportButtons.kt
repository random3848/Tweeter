package edu.csuci.tweeter.ui.report

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Report

/**
 * The flag beside another user's post that reports it. Logged-in users get the report dialog;
 * guests get Log in, as the like button does. Never shown on the user's own posts.
 *
 * @param postId The post reported.
 * @param authorUid The post's author, who is reported with it.
 * @param isLoggedIn Whether the user is logged in rather than a guest.
 * @param onLogIn Opens Log in, for guests.
 * @param modifier Applied to the button.
 */
@Composable
fun ReportPostButton(
    postId: String,
    authorUid: String,
    isLoggedIn: Boolean,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReporting by rememberSaveable(postId) { mutableStateOf(false) }

    IconButton(
        onClick = { if (isLoggedIn) isReporting = true else onLogIn() },
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_flag),
            contentDescription = stringResource(R.string.report_post),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (isReporting) {
        ReportDialog(
            reportedUid = authorUid,
            postId = postId,
            title = stringResource(R.string.report_post_title),
            onDismiss = { isReporting = false }
        )
    }
}

/**
 * The button on another user's profile that reports them. Logged-in users get the report dialog;
 * guests get Log in. Never shown on the user's own profile.
 *
 * @param userUid The user reported.
 * @param isLoggedIn Whether the user is logged in rather than a guest.
 * @param onLogIn Opens Log in, for guests.
 * @param modifier Applied to the button.
 */
@Composable
fun ReportUserButton(
    userUid: String,
    isLoggedIn: Boolean,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReporting by rememberSaveable(userUid) { mutableStateOf(false) }

    TextButton(
        onClick = { if (isLoggedIn) isReporting = true else onLogIn() },
        modifier = modifier
    ) {
        Text(text = stringResource(R.string.report_user))
    }

    if (isReporting) {
        ReportDialog(
            reportedUid = userUid,
            postId = Report.NO_POST,
            title = stringResource(R.string.report_user_title),
            onDismiss = { isReporting = false }
        )
    }
}
