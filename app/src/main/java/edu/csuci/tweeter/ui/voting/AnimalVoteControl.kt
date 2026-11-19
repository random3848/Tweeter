package edu.csuci.tweeter.ui.voting

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.FULL_AGREEMENT
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.ui.theme.AGREED_GREEN
import edu.csuci.tweeter.ui.theme.DISPUTED_YELLOW

/**
 * A post's animal name control, `[ name | ↑ | ↓ ]`: the animal the post shows, green while every
 * vote agrees with it and yellow otherwise, then the user's vote controls.
 *
 * The name opens the animal's page while every vote agrees, and the post's voting page otherwise.
 * For other users, ↑ votes for the animal the post shows, and ↓ opens the dialog that votes for
 * another; the arrow of the user's vote is highlighted, and tapping it again takes the vote back.
 * The poster has only ↓, which reclassifies the post as the animal they pick, and guests' arrows
 * open Log in.
 *
 * @param postId The post's ID.
 * @param posterAnimal The animal the poster named ([edu.csuci.tweeter.data.Post.animalType]).
 * @param shownAnimal The animal the post shows ([edu.csuci.tweeter.data.shownAnimalOf]).
 * @param agreement The share of the votes that name [shownAnimal].
 * @param round The post's current voting round, which a new vote is cast in.
 * @param voter Who the user is to the post.
 * @param userVote The animal the user's vote of [round] names, or null if they have none in it.
 * @param actions What the control's parts do.
 * @param onLogIn Opens Log in, for a guest who tries to vote.
 * @param modifier Applied to the control.
 */
@Composable
internal fun AnimalVoteControl(
    postId: String,
    posterAnimal: String,
    shownAnimal: String,
    agreement: Double,
    round: Int,
    voter: Voter,
    userVote: String?,
    actions: VoteActions,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAgreed = agreement == FULL_AGREEMENT
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimalName(
            name = shownAnimal,
            isAgreed = isAgreed,
            onClick = {
                if (isAgreed) actions.onOpenAnimal(shownAnimal) else actions.onOpenVoting(postId)
            },
            // Gives way to the arrows, so a long name is cut short instead of them.
            modifier = Modifier.weight(1f, fill = false)
        )
        VerticalDivider(
            modifier = Modifier.height(24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        when (voter) {
            Voter.GUEST -> {
                VoteArrows(
                    isAgreeing = false,
                    isDisagreeing = false,
                    onAgree = onLogIn,
                    onDisagree = onLogIn
                )
            }

            Voter.POSTER -> ReclassifyArrow(postId, posterAnimal, actions.onReclassify)

            Voter.USER -> {
                UserVoteArrows(
                    ballot = PostBallot(postId, round, userVote),
                    shownAnimal = shownAnimal,
                    onToggleVote = actions.onToggleVote
                )
            }
        }
    }
}

// The name of the animal a post shows, colored by whether every vote agrees with it.
@Composable
private fun AnimalName(
    name: String,
    isAgreed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openLabel = if (isAgreed) {
        R.string.animal_vote_open_posts
    } else {
        R.string.animal_vote_open_votes
    }
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = if (isAgreed) AGREED_GREEN else DISPUTED_YELLOW,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(openLabel),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

// A logged-in user's arrows on someone else's post, and the dialog ↓ opens to name another animal.
@Composable
private fun UserVoteArrows(
    ballot: PostBallot,
    shownAnimal: String,
    onToggleVote: (ballot: PostBallot, animal: String) -> Unit
) {
    var isPickingAnimal by rememberSaveable(ballot.postId) { mutableStateOf(false) }
    val userVote = ballot.userVote
    val otherAnimalVotedFor = userVote?.takeIf { animal -> animal != shownAnimal }
    VoteArrows(
        isAgreeing = userVote == shownAnimal,
        isDisagreeing = otherAnimalVotedFor != null,
        onAgree = { onToggleVote(ballot, shownAnimal) },
        onDisagree = {
            if (otherAnimalVotedFor != null) {
                // Toggling the animal the vote names takes the vote back.
                onToggleVote(ballot, otherAnimalVotedFor)
            } else {
                isPickingAnimal = true
            }
        }
    )
    if (isPickingAnimal) {
        AnimalVoteDialog(
            isOwnPost = false,
            onPick = { animal ->
                isPickingAnimal = false
                // Picking the animal the user already voted for keeps the vote.
                if (animal != userVote) {
                    onToggleVote(ballot, animal)
                }
            },
            onDismiss = { isPickingAnimal = false }
        )
    }
}

// The poster's ↓ on their own post, and the dialog it opens to reclassify the post.
@Composable
private fun ReclassifyArrow(
    postId: String,
    posterAnimal: String,
    onReclassify: (postId: String, animal: String) -> Unit
) {
    var isPickingAnimal by rememberSaveable(postId) { mutableStateOf(false) }
    VoteArrow(
        icon = R.drawable.ic_arrow_downward,
        description = R.string.animal_vote_reclassify,
        isChecked = false,
        onClick = { isPickingAnimal = true }
    )
    if (isPickingAnimal) {
        AnimalVoteDialog(
            isOwnPost = true,
            onPick = { animal ->
                isPickingAnimal = false
                // Picking the animal the post already names changes nothing.
                if (animal != posterAnimal) {
                    onReclassify(postId, animal)
                }
            },
            onDismiss = { isPickingAnimal = false }
        )
    }
}

// ↑ and ↓, each highlighted while the user's vote is what it stands for.
@Composable
private fun VoteArrows(
    isAgreeing: Boolean,
    isDisagreeing: Boolean,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    VoteArrow(
        icon = R.drawable.ic_arrow_upward,
        description = R.string.animal_vote_agree,
        isChecked = isAgreeing,
        onClick = onAgree
    )
    VoteArrow(
        icon = R.drawable.ic_arrow_downward,
        description = R.string.animal_vote_disagree,
        isChecked = isDisagreeing,
        onClick = onDisagree
    )
}

@Composable
private fun VoteArrow(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    IconToggleButton(checked = isChecked, onCheckedChange = { onClick() }) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = if (isChecked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
