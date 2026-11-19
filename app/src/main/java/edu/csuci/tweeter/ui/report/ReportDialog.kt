package edu.csuci.tweeter.ui.report

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Report
import edu.csuci.tweeter.data.ReportReason

/**
 * The dialog that reports user [reportedUid], or their post [postId]: a required reason, optional
 * details, then a confirmation once sent, or an error the user can send again from.
 *
 * @param reportedUid The user reported, or the reported post's author.
 * @param postId The reported post's ID, or [Report.NO_POST] for a profile.
 * @param title The dialog's title.
 * @param onDismiss Closes the dialog.
 * @param reportViewModel Holds the draft and sends it.
 */
@Composable
fun ReportDialog(
    reportedUid: String,
    postId: String,
    title: String,
    onDismiss: () -> Unit,
    reportViewModel: ReportViewModel = viewModel(
        key = "report/$reportedUid/$postId",
        factory = ReportViewModel.createFactory(reportedUid, postId)
    )
) {
    if (reportViewModel.status == ReportStatus.SENT) {
        ReportSentDialog(onDismiss = onDismiss)
        return
    }
    val isSending = reportViewModel.status == ReportStatus.SENDING
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { ReportForm(reportViewModel) },
        confirmButton = {
            TextButton(
                onClick = reportViewModel::send,
                enabled = reportViewModel.reason != null && !isSending
            ) {
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
