package edu.csuci.tweeter.ui.post

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R

/**
 * The post's animal field: the user types part of a name and chooses the animal from the
 * suggestions that drop down below it, or leaves it empty.
 *
 * @param picker Holds what the user typed, the suggestions, and the chosen animal.
 * @param modifier Applied to the field.
 */
@OptIn(ExperimentalMaterial3Api::class) // For ExposedDropdownMenuBox, still experimental.
@Composable
fun AnimalField(picker: AnimalPickerViewModel, modifier: Modifier = Modifier) {
    // Lets the user close the suggestions without choosing one; typing opens them again.
    var isMenuOpen by rememberSaveable { mutableStateOf(true) }
    val showsSuggestions = isMenuOpen && picker.suggestions.isNotEmpty()
    val hint = if (picker.isComplete) {
        R.string.post_animal_hint
    } else {
        R.string.post_animal_not_chosen
    }

    ExposedDropdownMenuBox(
        expanded = showsSuggestions,
        onExpandedChange = { isMenuOpen = it },
        modifier = modifier
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
