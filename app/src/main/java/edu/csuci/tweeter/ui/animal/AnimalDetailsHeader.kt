package edu.csuci.tweeter.ui.animal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Animal
import edu.csuci.tweeter.data.AnimalPhoto
import edu.csuci.tweeter.data.ConservationStatus
import java.text.NumberFormat

// Between the ranks of an animal's taxonomy, broadest first.
private const val TAXONOMY_SEPARATOR = " › "

// Photos of animals are this many times as wide as they are tall.
private const val PHOTO_ASPECT_RATIO = 4f / 3f

/**
 * The top of an animal's page: what iNaturalist knows about the animal, or why it shows nothing,
 * then the heading of the posts that name the animal.
 *
 * @param uiState What is known about the animal so far.
 * @param onRetry Looks the animal up again, after a lookup failed.
 */
@Composable
fun AnimalDetailsHeader(uiState: AnimalDetailsUiState, onRetry: () -> Unit) {
    Column {
        when (uiState) {
            AnimalDetailsUiState.Loading -> LookupInProgress()

            is AnimalDetailsUiState.Found -> AnimalOverview(animal = uiState.animal)

            AnimalDetailsUiState.NotFound -> LookupMessage(R.string.animal_not_found)

            AnimalDetailsUiState.Failed -> {
                LookupMessage(R.string.animal_lookup_failed)
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(R.string.animal_lookup_retry))
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = stringResource(R.string.animal_posts_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun LookupInProgress() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.animal_lookup_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LookupMessage(textResource: Int) {
    Text(
        text = stringResource(textResource),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
    )
}

// Everything iNaturalist knows about the animal, each part left out when it has none.
@Composable
private fun AnimalOverview(animal: Animal) {
    animal.photo?.let { AnimalPhotoWithCredit(photo = it, animalName = animal.name) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimalNames(animal)
        conservationStatusResource(animal)?.let { DetailLine(stringResource(it)) }
        DetailLine(observationCountText(animal.observationCount))
        WikipediaSummary(animal.wikipediaSummary)
        AnimalLinks(wikipediaUrl = animal.wikipediaUrl, pageUrl = animal.pageUrl)
    }
}

@Composable
private fun AnimalPhotoWithCredit(photo: AnimalPhoto, animalName: String) {
    AsyncImage(
        model = photo.url,
        contentDescription = stringResource(R.string.animal_photo_description, animalName),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_ASPECT_RATIO)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
    // The photo's license requires its credit.
    Text(
        text = photo.attribution,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

// The scientific name and the groups the animal belongs to; the top bar shows its common name.
@Composable
private fun AnimalNames(animal: Animal) {
    if (animal.scientificName.isNotBlank()) {
        Text(
            text = animal.scientificName,
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic
        )
    }
    if (animal.taxonomy.isNotEmpty()) {
        DetailLine(animal.taxonomy.joinToString(TAXONOMY_SEPARATOR))
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun observationCountText(count: Int): String = pluralStringResource(
    R.plurals.animal_observation_count,
    count,
    NumberFormat.getIntegerInstance().format(count)
)

// The opening of the animal's Wikipedia article; a link next to it credits the article.
@Composable
private fun WikipediaSummary(summary: String) {
    if (summary.isBlank()) {
        return
    }
    // Parsed once per summary, not on every recomposition.
    val text = remember(summary) { AnnotatedString.fromHtml(summary) }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

// Links to the animal's Wikipedia article and iNaturalist page, side by side where they fit.
@Composable
private fun AnimalLinks(wikipediaUrl: String?, pageUrl: String?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        wikipediaUrl?.let { url ->
            LinkButton(text = stringResource(R.string.animal_open_wikipedia), url = url)
        }
        pageUrl?.let { url ->
            LinkButton(text = stringResource(R.string.animal_open_inaturalist), url = url)
        }
    }
}

// Opens [url] in the browser. Its text lines up with the text around it.
@Composable
private fun LinkButton(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(url) },
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        Text(text)
    }
}

/** The string resource that names the animal's conservation status, or null if it has none. */
private fun conservationStatusResource(animal: Animal): Int? = when (animal.conservationStatus) {
    null -> null
    ConservationStatus.LEAST_CONCERN -> R.string.animal_status_least_concern
    ConservationStatus.NEAR_THREATENED -> R.string.animal_status_near_threatened
    ConservationStatus.VULNERABLE -> R.string.animal_status_vulnerable
    ConservationStatus.ENDANGERED -> R.string.animal_status_endangered
    ConservationStatus.CRITICALLY_ENDANGERED -> R.string.animal_status_critically_endangered
    ConservationStatus.EXTINCT_IN_THE_WILD -> R.string.animal_status_extinct_in_the_wild
    ConservationStatus.EXTINCT -> R.string.animal_status_extinct
}
