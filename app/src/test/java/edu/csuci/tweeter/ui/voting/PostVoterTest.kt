@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.FakeVoteRepository
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.SlowVoteRepository
import edu.csuci.tweeter.data.UnsavableVoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val POST_ID = "post"
private const val OTHER_POST_ID = "other post"
private const val ROUND = 1
private const val POSSUM = "Possum"
private const val RACCOON = "Raccoon"

class PostVoterTest {
    @Test
    fun toggleVote_noVote_votesForAnimal() = runTest {
        val votes = FakeVoteRepository()

        PostVoter(votes).toggleVote(ballotOf(POST_ID, userVote = null), POSSUM)

        assertEquals(mapOf(POST_ID to AnimalVote(POSSUM, ROUND)), votes.getVotes().first())
    }

    @Test
    fun toggleVote_voteForAnimal_withdrawsIt() = runTest {
        val votes = votesWithUsersVoteFor(POSSUM)

        PostVoter(votes).toggleVote(ballotOf(POST_ID, userVote = POSSUM), POSSUM)

        assertEquals(emptyMap<String, AnimalVote>(), votes.getVotes().first())
    }

    @Test
    fun toggleVote_voteForOtherAnimal_movesItToAnimal() = runTest {
        val votes = votesWithUsersVoteFor(RACCOON)

        PostVoter(votes).toggleVote(ballotOf(POST_ID, userVote = RACCOON), POSSUM)

        assertEquals(mapOf(POST_ID to AnimalVote(POSSUM, ROUND)), votes.getVotes().first())
    }

    @Test
    fun toggleVote_notSaved_doesNotThrow() = runTest {
        val votes = UnsavableVoteRepository()

        PostVoter(votes).toggleVote(ballotOf(POST_ID, userVote = null), POSSUM)

        assertEquals(1, votes.attemptCount)
    }

    @Test
    fun toggleVote_afterChangeNotSaved_triesAgain() = runTest {
        val votes = UnsavableVoteRepository()
        val voter = PostVoter(votes)
        voter.toggleVote(ballotOf(POST_ID, userVote = null), POSSUM)

        voter.toggleVote(ballotOf(POST_ID, userVote = null), POSSUM)

        assertEquals(2, votes.attemptCount)
    }

    @Test
    fun toggleVote_tappedAgainBeforeSaved_changesOnce() = runTest {
        val votes = SlowVoteRepository()
        val voter = PostVoter(votes)

        startVote(voter, POST_ID)
        startVote(voter, POST_ID)

        assertEquals(1, votes.changeCount)
    }

    @Test
    fun toggleVote_otherPostWhileOneIsSaving_changesBoth() = runTest {
        val votes = SlowVoteRepository()
        val voter = PostVoter(votes)

        startVote(voter, POST_ID)
        startVote(voter, OTHER_POST_ID)

        assertEquals(2, votes.changeCount)
    }

    @Test
    fun toggleVote_afterLastChangeSaved_changesAgain() = runTest {
        val votes = SlowVoteRepository()
        val voter = PostVoter(votes)
        startVote(voter, POST_ID)
        votes.finishChanges()

        startVote(voter, POST_ID)

        assertEquals(2, votes.changeCount)
    }

    @Test
    fun reclassify_savesIt() = runTest {
        val votes = FakeVoteRepository()

        PostVoter(votes).reclassify(POST_ID, POSSUM)

        assertEquals(mapOf(POST_ID to POSSUM), votes.reclassifiedAnimals.value)
    }

    @Test
    fun reclassify_notSaved_doesNotThrow() = runTest {
        val votes = UnsavableVoteRepository()

        PostVoter(votes).reclassify(POST_ID, POSSUM)

        assertEquals(1, votes.attemptCount)
    }

    @Test
    fun reclassify_whileVoteChangeOfPostIsSaving_isIgnored() = runTest {
        val votes = SlowVoteRepository()
        val voter = PostVoter(votes)
        startVote(voter, POST_ID)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            voter.reclassify(POST_ID, POSSUM)
        }

        assertEquals(1, votes.changeCount)
    }

    private fun ballotOf(postId: String, userVote: String?) = PostBallot(postId, ROUND, userVote)

    // The votes when the user's vote on the post, the only one on it, names [animal].
    private fun votesWithUsersVoteFor(animal: String) = FakeVoteRepository(
        initialVotes = mapOf(POST_ID to AnimalVote(animal, ROUND)),
        initialVoteCounts = mapOf(POST_ID to mapOf(animal to 1))
    )

    // Starts voting on the post, which waits for the slow repository until the test finishes it.
    private fun TestScope.startVote(voter: PostVoter, postId: String) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            voter.toggleVote(ballotOf(postId, userVote = null), POSSUM)
        }
    }
}
