package edu.csuci.tweeter.ui.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.CenteredMessage
import edu.csuci.tweeter.ui.feed.PhotoCard
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * The voting page of one post, opened by the post's animal name while votes disagree: the post's
 * photo, then a row for each animal voted for, with how many voted for it and a bar of its share
 * of the votes. The poster's animal comes first, marked with the poster's initials. An animal's
 * name opens its page, and the arrow beside it, for users other than the poster, votes for it, or
 * takes the vote back when it's theirs already; guests' arrows open Log in.
 *
 * @param postId The post whose votes it shows.
 * @param onBack Returns to the screen it was opened from.
 * @param onOpenAnimal Opens the page of the animal with the given name.
 * @param onLogIn Opens Log in, for a guest who tries to vote.
 * @param modifier Applied to the whole screen.
 * @param viewModel Supplies the post and its votes.
 */
@Composable
fun VotingScreen(
    postId: String,
    onBack: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VotingViewModel = viewModel(factory = VotingViewModel.createFactory(postId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.voting_title), onBack = onBack)
        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        when {
            uiState.isLoading -> {
                Box(modifier = bodyModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.isPostGone -> {
                CenteredMessage(
                    text = stringResource(R.string.voting_post_gone),
                    modifier = bodyModifier
                )
            }

            else -> {
                PostVotesList(
                    uiState = uiState,
                    onOpenAnimal = onOpenAnimal,
                    onVote = voteActionFor(uiState.voter, viewModel::toggleVote, onLogIn),
                    modifier = bodyModifier
                )
            }
        }
    }
}

// What a share's arrow does for [voter]: votes for its animal, opens Log in for a guest, or
// nothing for the poster, who gets no arrows.
private fun voteActionFor(
    voter: Voter,
    toggleVote: (String) -> Unit,
    onLogIn: () -> Unit
): ((String) -> Unit)? = when (voter) {
    Voter.GUEST -> { _: String -> onLogIn() }
    Voter.POSTER -> null
    Voter.USER -> toggleVote
}

// The post's photo, then its shares.
@Composable
private fun PostVotesList(
    uiState: VotingUiState,
    onOpenAnimal: (String) -> Unit,
    onVote: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            PhotoCard(imageUri = uiState.imageUri)
        }
        items(uiState.shares, key = { share -> share.animal }) { share ->
            AnimalShareRow(
                share = share,
                posterInitials = uiState.posterInitials,
                isVotedFor = uiState.ballot?.userVote == share.animal,
                onOpenAnimal = onOpenAnimal,
                onVote = onVote
            )
        }
    }
}

/**
 * One animal voted for: its name, which opens its page, how many voted for it, the arrow that
 * votes for it, and the bar of its share under them.
 *
 * @param share The animal and its votes.
 * @param posterInitials The poster's initials, shown before the poster's animal.
 * @param isVotedFor Whether the user's vote names the animal, which highlights its arrow.
 * @param onOpenAnimal Opens the page of the animal with the given name.
 * @param onVote Votes for the animal with the given name, or takes the vote back; null for the
 *     poster, who can't vote, and gets no arrow.
 */
@Composable
private fun AnimalShareRow(
    share: AnimalShare,
    posterInitials: String,
    isVotedFor: Boolean,
    onOpenAnimal: (String) -> Unit,
    onVote: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (share.isPosterChoice) {
                PosterMark(initials = posterInitials)
            }
            ShareName(
                animal = share.animal,
                onClick = { onOpenAnimal(share.animal) },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = pluralStringResource(
                    R.plurals.voting_vote_count,
                    share.voteCount,
                    share.voteCount
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onVote != null) {
                ShareVoteArrow(
                    animal = share.animal,
                    isVotedFor = isVotedFor,
                    onClick = { onVote(share.animal) }
                )
            }
        }
        LinearProgressIndicator(
            progress = { share.share.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// The poster's avatar initials, in a small circle, marking the animal the poster named.
@Composable
private fun PosterMark(initials: String) {
    val description = stringResource(R.string.voting_poster_choice)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ShareName(animal: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = animal,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clickable(
                onClickLabel = stringResource(R.string.voting_open_animal),
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 8.dp)
    )
}

// The arrow that votes for [animal], highlighted while the user's vote names it.
@Composable
private fun ShareVoteArrow(animal: String, isVotedFor: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = isVotedFor, onCheckedChange = { onClick() }) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_upward),
            contentDescription = stringResource(R.string.voting_vote_for, animal),
            tint = if (isVotedFor) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
