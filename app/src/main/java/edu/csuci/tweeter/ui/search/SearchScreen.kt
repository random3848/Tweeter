package edu.csuci.tweeter.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import edu.csuci.tweeter.ui.profile.ProfileAvatar

/**
 * The Search tab: a field to search with, under the app's top bar, and the people and animals
 * whose names contain what the user types. Pressing a person opens their profile; pressing an
 * animal opens the posts that name it.
 *
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onOpenAnimal Opens the posts of the animal with the given name.
 * @param modifier Applied to the whole screen.
 * @param viewModel Holds the query and finds the results.
 */
@Composable
fun SearchScreen(
    onOpenProfile: (String) -> Unit,
    onOpenAnimal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory)
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        TweeterTopBar()
        SearchField(
            query = viewModel.query,
            onQueryChange = viewModel::updateQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        when {
            viewModel.query.isBlank() -> {
                CenteredMessage(
                    text = stringResource(R.string.search_prompt),
                    modifier = bodyModifier
                )
            }

            results.isEmpty() -> {
                CenteredMessage(
                    text = stringResource(R.string.search_no_results, viewModel.query.trim()),
                    modifier = bodyModifier
                )
            }

            else -> {
                SearchResultsList(
                    results = results,
                    onOpenProfile = onOpenProfile,
                    onOpenAnimal = onOpenAnimal,
                    modifier = bodyModifier
                )
            }
        }
    }
}

// Whether nothing was found, once the people have been read.
private fun SearchResults.isEmpty(): Boolean =
    !isLoadingPeople && people.isEmpty() && animals.isEmpty()

/**
 * The field the user types the names they search for into. The keyboard's search key closes the
 * keyboard, since the results follow the text as it's typed.
 *
 * @param query The text in the field.
 * @param onQueryChange Called with the text the user typed.
 * @param modifier Applied to the field.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        label = { Text(stringResource(R.string.search_field_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
    )
}

/**
 * The people found, then the animals, each under its heading. While the people are still being
 * read, their heading says so.
 *
 * @param results What was found.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onOpenAnimal Opens the posts of the animal with the given name.
 * @param modifier Applied to the list.
 */
@Composable
private fun SearchResultsList(
    results: SearchResults,
    onOpenProfile: (String) -> Unit,
    onOpenAnimal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (results.isLoadingPeople) {
            sectionHeading(R.string.search_people_loading)
        } else if (results.people.isNotEmpty()) {
            sectionHeading(R.string.search_people)
            items(results.people, key = { "person:${it.uid}" }) { profile ->
                PersonResult(profile = profile, onOpenProfile = onOpenProfile)
            }
        }
        if (results.animals.isNotEmpty()) {
            sectionHeading(R.string.search_animals)
            items(results.animals, key = { "animal:${it.name}" }) { animal ->
                AnimalResult(animal = animal, onOpenAnimal = onOpenAnimal)
            }
        }
    }
}

// A heading above a group of results, which screen readers can jump between.
private fun LazyListScope.sectionHeading(@StringRes text: Int) {
    item(key = "heading:$text") {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                .semantics { heading() }
        )
    }
}

/**
 * A person found: their picture and name.
 *
 * @param profile The person's profile.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 */
@Composable
private fun PersonResult(profile: UserProfile, onOpenProfile: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(profile.displayName) },
        modifier = Modifier.clickable { onOpenProfile(profile.uid) },
        leadingContent = {
            ProfileAvatar(
                profile = profile,
                size = 40.dp,
                initialsStyle = MaterialTheme.typography.titleMedium
            )
        }
    )
}

/**
 * An animal found: its name, and how many posts name it.
 *
 * @param animal The animal.
 * @param onOpenAnimal Opens the posts of the animal with the given name.
 */
@Composable
private fun AnimalResult(animal: PostedAnimal, onOpenAnimal: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(animal.name) },
        modifier = Modifier.clickable { onOpenAnimal(animal.name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.search_animal_post_count,
                    animal.postCount,
                    animal.postCount
                )
            )
        }
    )
}

/**
 * [text] in the middle of the space below the search field.
 *
 * @param text What to say.
 * @param modifier Applied to the space.
 */
@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
