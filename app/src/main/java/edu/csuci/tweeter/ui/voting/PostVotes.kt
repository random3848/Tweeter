package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.AnimalVote

/**
 * The user's votes on what animal posts show, as a list of posts shows them on each post's
 * [AnimalVoteControl], and what the controls do. The list finds each post's vote in [userVotes]
 * and gives the control only that, so a change to one vote doesn't redraw every control.
 *
 * @property userVotes The user's votes, by post ID.
 * @property actions What the controls' parts do.
 */
internal data class PostVotes(val userVotes: Map<String, AnimalVote>, val actions: VoteActions)
