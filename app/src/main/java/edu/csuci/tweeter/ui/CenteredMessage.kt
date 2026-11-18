package edu.csuci.tweeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * [text] in the middle of a screen's empty body, such as "No photos shared yet." on an empty
 * feed, and under it what the user can do about it, if anything.
 *
 * @param text What to say.
 * @param modifier Applied to the space the text is centered in.
 * @param action What the user can do, such as a retry button.
 */
@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action()
    }
}
