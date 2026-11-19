package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [VoteRepository] for tests, holding the logged-in user's votes in memory, starting with
 * [initialVotes], and each post's vote counts, starting with [initialVoteCounts], which the
 * user's vote changes move between as Firestore would. It leaves posts alone, which the post
 * repository holds, and records each reclassification in [reclassifiedAnimals] instead.
 */
class FakeVoteRepository(
    initialVotes: Map<String, AnimalVote> = emptyMap(),
    initialVoteCounts: Map<String, Map<String, Int>> = emptyMap()
) : VoteRepository {
    private val votes = MutableStateFlow(initialVotes)
    private val voteCounts = MutableStateFlow(initialVoteCounts)
    private val _reclassifiedAnimals = MutableStateFlow(emptyMap<String, String>())

    /** The animal each reclassified post now names, by the post's ID. */
    val reclassifiedAnimals: StateFlow<Map<String, String>> = _reclassifiedAnimals.asStateFlow()

    override fun getVotes(): Flow<Map<String, AnimalVote>> = votes

    override fun getVoteCounts(postId: String): Flow<Map<String, Int>> =
        voteCounts.map { counts -> counts[postId].orEmpty() }

    override suspend fun changeVote(change: VoteChange) {
        votes.update { current -> current.withVoteOf(change) }
        voteCounts.update { counts ->
            val postCounts = counts[change.postId].orEmpty()
            counts + (change.postId to postCounts.plusIncrements(change.countIncrements))
        }
    }

    override suspend fun reclassify(postId: String, animal: String) {
        _reclassifiedAnimals.update { it + (postId to animal) }
        voteCounts.update { it - postId }
    }

    private fun Map<String, AnimalVote>.withVoteOf(change: VoteChange): Map<String, AnimalVote> {
        val newAnimal = change.newAnimal ?: return this - change.postId
        return this + (change.postId to AnimalVote(newAnimal, change.round))
    }

    // These counts with [increments] added, leaving out animals no vote is left for.
    private fun Map<String, Int>.plusIncrements(increments: Map<String, Int>): Map<String, Int> {
        val counts = toMutableMap()
        for ((animal, increment) in increments) {
            counts[animal] = counts.getOrDefault(animal, 0) + increment
        }
        return counts.filterValues { count -> count > 0 }
    }
}
