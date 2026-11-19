package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.PostBallot

/**
 * What the parts of a post's [AnimalVoteControl] do. Each callback takes the post's ID or values
 * rather than capturing a table, so it stays the same as new tables arrive.
 *
 * @property onToggleVote Votes for the given animal instead of the given ballot's vote, or takes
 *     the vote back if it already names that animal. Only logged-in users get to vote.
 * @property onReclassify Has the user's own post with the given ID name the given animal instead.
 * @property onOpenAnimal Opens the page of the animal with the given name.
 * @property onOpenVoting Opens the voting page of the post with the given ID.
 */
internal data class VoteActions(
    val onToggleVote: (ballot: PostBallot, animal: String) -> Unit,
    val onReclassify: (postId: String, animal: String) -> Unit,
    val onOpenAnimal: (String) -> Unit,
    val onOpenVoting: (String) -> Unit
)
