package edu.csuci.tweeter.ui.report

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportReason

/**
 * The dialog that reports what [reportViewModel] was told to with
 * [ReportViewModel.startReport]: a required reason and optional details, then a spinner until the
 * server has the report, and a confirmation once it does, or an error the user can send again
 * from.
 *
 * @param title The dialog's title.
 * @param onDismiss Closes the dialog, after the draft is discarded so the next one opens empty.
 *     A report being sent is kept, and goes on sending.
 * @param reportViewModel Holds the draft and sends it.
 */
@Composable
fun ReportDialog(title: String, onDismiss: () -> Unit, reportViewModel: ReportViewModel) {
    val dismiss = {
        reportViewModel.discardDraft()
        onDismiss()
    }
    when (reportViewModel.status) {
        ReportStatus.EDITING, ReportStatus.FAILED -> {
            ReportFormDialog(
                title = title,
                onDismiss = dismiss,
                reportViewModel = reportViewModel
            )
        }

        ReportStatus.SENDING -> ReportSendingDialog(title = title, onDismiss = dismiss)

        ReportStatus.SENT -> ReportSentDialog(onDismiss = dismiss)
    }
}

@Composable
private fun ReportFormDialog(
    title: String,
    onDismiss: () -> Unit,
    reportViewModel: ReportViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { ReportForm(reportViewModel) },
        confirmButton = {
            TextButton(onClick = reportViewModel::send, enabled = reportViewModel.reason != null) {
                Text(stringResource(R.string.report_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.report_cancel))
            }
        }
    )
}

@Composable
private fun ReportForm(reportViewModel: ReportViewModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            ReportReason.entries.forEach { reason ->
                ReasonOption(
                    label = stringResource(labelOf(reason)),
                    isSelected = reason == reportViewModel.reason,
                    onSelect = { reportViewModel.chooseReason(reason) }
                )
            }
        }
        OutlinedTextField(
            value = reportViewModel.details,
            onValueChange = reportViewModel::changeDetails,
            label = { Text(stringResource(R.string.report_details)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.report_details_count,
                        reportViewModel.details.length,
                        Report.MAX_DETAILS_LENGTH
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (reportViewModel.status == ReportStatus.FAILED) {
            Text(
                text = stringResource(R.string.report_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ReasonOption(label: String, isSelected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@StringRes
private fun labelOf(reason: ReportReason): Int = when (reason) {
    ReportReason.SPAM -> R.string.report_reason_spam
    ReportReason.HARASSMENT -> R.string.report_reason_harassment
    ReportReason.SEXUAL_CONTENT -> R.string.report_reason_sexual_content
    ReportReason.VIOLENCE -> R.string.report_reason_violence
    ReportReason.OTHER -> R.string.report_reason_other
}

// Shown in place of the form, which can't change a report already sent. Closing it doesn't stop
// the report, which Firestore keeps queued while offline, so its only button is Close.
@Composable
private fun ReportSendingDialog(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SendingProgress() },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.report_close))
            }
        }
    )
}

@Composable
private fun SendingProgress() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.report_sending),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Text(
            text = stringResource(R.string.report_sending_queued),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReportSentDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_sent_title)) },
        text = { Text(stringResource(R.string.report_sent_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.report_sent_done))
            }
        }
    )
}
