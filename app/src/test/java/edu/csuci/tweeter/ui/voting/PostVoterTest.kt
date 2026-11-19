@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.FakeVoteRepository
import edu.csuci.tweeter.data.SlowVoteRepository
import edu.csuci.tweeter.data.UnsavableVoteRepository
import edu.csuci.tweeter.data.VoteChange
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

class PostVoterTest {
    @Test
    fun changeVote_savesIt() = runTest {
        val votes = FakeVoteRepository()

        PostVoter(votes).changeVote(voteFor(POSSUM, POST_ID))

        assertEquals(mapOf(POST_ID to AnimalVote(POSSUM, ROUND)), votes.getVotes().first())
    }

    @Test
    fun changeVote_notSaved_doesNotThrow() = runTest {
        val votes = UnsavableVoteRepository()

        PostVoter(votes).changeVote(voteFor(POSSUM, POST_ID))

        assertEquals(1, votes.attemptCount)
    }

    @Test
    fun changeVote_afterChangeNotSaved_triesAgain() = runTest {
        val votes = UnsavableVoteRepository()
        val voter = PostVoter(votes)
        voter.changeVote(voteFor(POSSUM, POST_ID))

        voter.changeVote(voteFor(POSSUM, POST_ID))

        assertEquals(2, votes.attemptCount)
    }

    @Test
    fun changeVote_tappedAgainBeforeSaved_changesOnce() = runTest {
        val votes = SlowVoteRepository()
        val voter = PostVoter(votes)

        startVote(voter, POST_ID)
        startVote(voter, POST_ID)

        assertEquals(1, votes.changeCount)
    }

    @Test
    fun changeVote_otherPostWhileOneIsSaving_changesBoth() = runTest {
        val votes = SlowVoteRepository()
        val voter = PostVoter(votes)

        startVote(voter, POST_ID)
        startVote(voter, OTHER_POST_ID)

        assertEquals(2, votes.changeCount)
    }

    @Test
    fun changeVote_afterLastChangeSaved_changesAgain() = runTest {
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

    private fun voteFor(animal: String, postId: String) =
        VoteChange(postId, ROUND, previousAnimal = null, newAnimal = animal)

    // Starts voting on the post, which waits for the slow repository until the test finishes it.
    private fun TestScope.startVote(voter: PostVoter, postId: String) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            voter.changeVote(voteFor(POSSUM, postId))
        }
    }
}
