package edu.csuci.tweeter.ui.voting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.post.AnimalPickerViewModel

// The key of the dialog's picker among the screen's ViewModels. A screen opens one dialog at a
// time, so every post's dialog shares one picker, emptied whenever a dialog closes.
private const val PICKER_KEY = "animalVoteDialog"

/**
 * The dialog that asks "What animal do you think it is?": the user searches the animals
 * [edu.csuci.tweeter.data.AnimalRepository] knows, and picks one of them.
 *
 * @param isOwnPost Whether the post is the user's own, which picking an animal reclassifies, as a
 *     note in the dialog warns.
 * @param onPick Called with the animal the user picked; the dialog is then closed by the caller.
 * @param onDismiss Closes the dialog without picking an animal.
 * @param picker Searches the animals as the user types.
 */
@Composable
internal fun AnimalVoteDialog(
    isOwnPost: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    picker: AnimalPickerViewModel = viewModel(
        key = PICKER_KEY,
        factory = AnimalPickerViewModel.factory
    )
) {
    val close = {
        picker.clear()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = close) {
                Text(stringResource(R.string.animal_vote_dialog_cancel))
            }
        },
        title = { Text(stringResource(R.string.animal_vote_dialog_title)) },
        text = {
            AnimalSearch(
                isOwnPost = isOwnPost,
                picker = picker,
                onPick = { animal ->
                    picker.clear()
                    onPick(animal)
                }
            )
        }
    )
}

// The search field and the animals that match it, with the note for a poster above them.
@Composable
private fun AnimalSearch(
    isOwnPost: Boolean,
    picker: AnimalPickerViewModel,
    onPick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isOwnPost) {
            Text(
                text = stringResource(R.string.animal_vote_dialog_reclassify_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = picker.query,
            onValueChange = picker::updateQuery,
            label = { Text(stringResource(R.string.animal_vote_dialog_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Bounded, since the dialog doesn't scroll its content itself.
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            items(picker.suggestions, key = { animal -> animal }) { animal ->
                Text(
                    text = animal,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { onPick(animal) })
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}
