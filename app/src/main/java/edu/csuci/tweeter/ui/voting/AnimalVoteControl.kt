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
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.VoteChange
import edu.csuci.tweeter.data.countedVoteOn
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
 * A post that shows no animal, since its poster didn't know what it is and no animal has a
 * majority yet, asks "What animal is this?" in yellow instead of a name, which opens its voting
 * page. With nothing to agree with, it has no ↑, and other users' ↓ suggests an animal.
 *
 * @param postId The post's ID.
 * @param posterAnimal The animal the poster named ([edu.csuci.tweeter.data.Post.animalType]).
 * @param shownAnimal The animal the post shows ([edu.csuci.tweeter.data.shownAnimalOf]), blank for
 *     none.
 * @param agreement The share of the votes that name [shownAnimal].
 * @param round The post's current voting round, the one the user's vote counts in.
 * @param voter Who the user is to the post.
 * @param votes The user's votes, and what the control's parts do.
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
    votes: PostVotes,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val identification = Identification.of(shownAnimal, agreement)
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimalName(
            name = shownAnimal,
            identification = identification,
            onClick = {
                if (identification == Identification.AGREED) {
                    votes.onOpenAnimal(shownAnimal)
                } else {
                    votes.onOpenVoting(postId)
                }
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
                    onAgree = onLogIn.takeIf { identification.hasShownAnimal },
                    onDisagree = onLogIn
                )
            }

            Voter.POSTER -> ReclassifyArrow(postId, posterAnimal, votes.onReclassify)

            Voter.USER -> {
                val userVote = votes.userVotes.countedVoteOn(postId, round)
                UserVoteArrows(
                    ballot = PostBallot(postId, round, userVote),
                    shownAnimal = shownAnimal,
                    canAgree = identification.hasShownAnimal,
                    onChangeVote = votes.onChangeVote
                )
            }
        }
    }
}

// The name of the animal a post shows, colored by whether every vote agrees with it, or the
// question that asks what it is while it shows none.
@Composable
private fun AnimalName(
    name: String,
    identification: Identification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = when (identification) {
        Identification.UNKNOWN -> stringResource(R.string.animal_vote_unknown)
        Identification.AGREED, Identification.DISPUTED -> name
    }
    val isAgreed = identification == Identification.AGREED
    val openLabel = if (isAgreed) {
        R.string.animal_vote_open_posts
    } else {
        R.string.animal_vote_open_votes
    }
    Text(
        text = text,
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
// Without an animal shown, [canAgree] is false and there is no ↑.
@Composable
private fun UserVoteArrows(
    ballot: PostBallot,
    shownAnimal: String,
    canAgree: Boolean,
    onChangeVote: (VoteChange) -> Unit
) {
    var isPickingAnimal by rememberSaveable(ballot.postId) { mutableStateOf(false) }
    val isDisagreeing = ballot.disagreesWith(shownAnimal)
    val agree = { onChangeVote(ballot.toggling(shownAnimal)) }
    VoteArrows(
        isAgreeing = ballot.agreesWith(shownAnimal),
        isDisagreeing = isDisagreeing,
        onAgree = agree.takeIf { canAgree },
        onDisagree = {
            if (isDisagreeing) {
                ballot.withdrawal()?.let(onChangeVote)
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
                ballot.changeTo(animal)?.let(onChangeVote)
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

// ↑ and ↓, each highlighted while the user's vote is what it stands for. Without [onAgree] there
// is no ↑, since the post shows no animal to agree with, and ↓ suggests one instead of
// disagreeing.
@Composable
private fun VoteArrows(
    isAgreeing: Boolean,
    isDisagreeing: Boolean,
    onAgree: (() -> Unit)?,
    onDisagree: () -> Unit
) {
    if (onAgree != null) {
        VoteArrow(
            icon = R.drawable.ic_arrow_upward,
            description = R.string.animal_vote_agree,
            isChecked = isAgreeing,
            onClick = onAgree
        )
    }
    VoteArrow(
        icon = R.drawable.ic_arrow_downward,
        description = if (onAgree == null) {
            R.string.animal_vote_suggest
        } else {
            R.string.animal_vote_disagree
        },
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
