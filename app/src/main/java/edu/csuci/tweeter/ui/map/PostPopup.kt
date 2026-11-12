package edu.csuci.tweeter.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.feed.PhotoCard
import edu.csuci.tweeter.ui.timeAgoText

private val POPUP_WIDTH = 200.dp
private val TAIL_WIDTH = 16.dp
private val TAIL_HEIGHT = 8.dp

/**
 * The popup a post's pin opens on the map: the post's photo, then who shared it and when, above a
 * tail that points down at the pin. Pressing it shows the post on Home.
 *
 * @param posts The posts on the map.
 * @param row The row in [posts] of the post whose pin the user tapped.
 * @param onClick Shows the post on Home.
 * @param modifier Applied to the popup, tail included.
 */
@Composable
internal fun PostPopup(
    posts: PostTable,
    row: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier.width(POPUP_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = color, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.map_popup_open),
                    role = Role.Button,
                    onClick = onClick
                )
            ) {
                PhotoCard(
                    imageUri = posts.imageUri(row),
                    contentDescription = stringResource(R.string.map_post_photo_description)
                )
                Text(
                    text = stringResource(
                        R.string.post_details,
                        posts.username(row),
                        timeAgoText(posts.timestampMillis(row))
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Canvas(modifier = Modifier.size(TAIL_WIDTH, TAIL_HEIGHT)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(tail, color)
        }
    }
}
