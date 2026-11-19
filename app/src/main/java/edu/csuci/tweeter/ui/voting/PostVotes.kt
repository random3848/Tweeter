package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.VoteChange

/**
 * The user's votes on what animal posts show, as a list of posts shows them on each post's
 * [AnimalVoteControl], and what the control's parts do. Each callback takes the post's ID or
 * values rather than capturing a table, so it stays the same as new tables arrive.
 *
 * @property userVotes The user's votes, by post ID.
 * @property onChangeVote Saves a change to the user's vote. Only logged-in users get to make one.
 * @property onReclassify Has the user's own post with the given ID name the given animal instead.
 * @property onOpenAnimal Opens the page of the animal with the given name.
 * @property onOpenVoting Opens the voting page of the post with the given ID.
 */
internal data class PostVotes(
    val userVotes: Map<String, AnimalVote>,
    val onChangeVote: (VoteChange) -> Unit,
    val onReclassify: (postId: String, animal: String) -> Unit,
    val onOpenAnimal: (String) -> Unit,
    val onOpenVoting: (String) -> Unit
)
