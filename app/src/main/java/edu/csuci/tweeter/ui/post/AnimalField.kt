package edu.csuci.tweeter.ui.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R

/**
 * The post's animal field: the user types part of a name and chooses the animal from the
 * suggestions that drop down below it, or says they don't know what it is, which empties the field
 * so that the post asks others. Leaving the field empty asks them too.
 *
 * @param picker Holds what the user typed, the suggestions, the chosen animal, and whether they
 *     said they don't know it.
 * @param modifier Applied to the field and the choice under it.
 */
@Composable
fun AnimalField(picker: AnimalPickerViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnimalSearchField(picker = picker)
        UnknownAnimalChoice(
            isUnknown = picker.isUnknown,
            onChange = { isUnknown ->
                if (isUnknown) picker.markUnknown() else picker.clear()
            }
        )
    }
}

// The text field that searches the animals, and the suggestions under it.
@OptIn(ExperimentalMaterial3Api::class) // For ExposedDropdownMenuBox, still experimental.
@Composable
private fun AnimalSearchField(picker: AnimalPickerViewModel) {
    // Lets the user close the suggestions without choosing one; typing opens them again.
    var isMenuOpen by rememberSaveable { mutableStateOf(true) }
    val showsSuggestions = isMenuOpen && picker.suggestions.isNotEmpty()
    val hint = when {
        picker.isUnknown -> R.string.post_animal_unknown_hint
        picker.isComplete -> R.string.post_animal_hint
        else -> R.string.post_animal_not_chosen
    }

    ExposedDropdownMenuBox(
        expanded = showsSuggestions,
        onExpandedChange = { isMenuOpen = it }
    ) {
        OutlinedTextField(
            value = picker.query,
            onValueChange = { text ->
                isMenuOpen = true
                picker.updateQuery(text)
            },
            label = { Text(stringResource(R.string.post_animal_label)) },
            supportingText = { Text(stringResource(hint)) },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = showsSuggestions,
            onDismissRequest = { isMenuOpen = false }
        ) {
            picker.suggestions.forEach { animal ->
                DropdownMenuItem(
                    text = { Text(animal) },
                    onClick = { picker.chooseAnimal(animal) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

// "I don't know what it is", checked while the user says so. Checking it empties the field, and
// unchecking it leaves the field empty for them to search.
@Composable
private fun UnknownAnimalChoice(isUnknown: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        // As tall as the minimum touch target, which the checkbox, drawn without its own click
        // handling, doesn't give the row.
        modifier = Modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = isUnknown,
                role = Role.Checkbox,
                onValueChange = onChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The row takes the taps, so that its text toggles the box too.
        Checkbox(checked = isUnknown, onCheckedChange = null)
        Text(
            text = stringResource(R.string.post_animal_unknown),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
