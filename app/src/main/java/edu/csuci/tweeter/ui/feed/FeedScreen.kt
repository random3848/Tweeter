package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.ui.theme.TweeterTheme

/**
 * The home feed: every post, newest first.
 *
 * For now each post is only its username and caption, which shows the posts arriving from the
 * repository; the post layout from the designs replaces it.
 *
 * @param modifier Applied to the list.
 * @param viewModel Supplies the posts.
 */
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    PostList(posts = posts, modifier = modifier)
}

/** [posts] as a scrolling list, one username and caption each. */
@Composable
private fun PostList(posts: List<Post>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(posts, key = { it.id }) { post ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = post.username, style = MaterialTheme.typography.titleMedium)
                Text(text = post.caption, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostListPreview() {
    TweeterTheme {
        PostList(posts = SAMPLE_POSTS)
    }
}
