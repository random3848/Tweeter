package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * A [VoteRepository] that stores votes in Cloud Firestore, as the documents
 * `users/{uid}/votes/{postId}` that [VoteDocument] describes. A vote and the post's
 * [Post.voteCounts] change together, in one batch, and the Firestore rules refuse counts that
 * change otherwise than by the user's vote, so the two always agree. Reclassifying a post clears
 * its counts and starts its next [Post.voteRound], which leaves the votes of earlier rounds
 * uncounted where they are, since only their voters may delete them.
 *
 * The user's votes come from one listener on their votes, set up again whenever someone else logs
 * in, and again with [retryWithBackoff] when it fails, as for [FirestoreLikeRepository]'s likes.
 * A post's counts come from a listener on the post, which signs a guest in first if nobody is
 * signed in.
 *
 * @param accountRepository Who is logged in, whose votes these are.
 * @param guestSignIn Signs a guest in if nobody is signed in, before a listener is set up.
 * @param dispatcher Where the listeners deliver snapshots and they are read, off the main thread.
 */
class FirestoreVoteRepository(
    private val accountRepository: AccountRepository,
    private val guestSignIn: GuestSignIn,
    private val firestore: FirebaseFirestore,
    private val dispatcher: CoroutineDispatcher
) : VoteRepository {
    private val posts = firestore.collection(PostDocument.COLLECTION)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getVotes(): Flow<Map<String, AnimalVote>> =
        accountRepository.loggedInUid.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyMap()) else votesOf(uid)
        }

    override fun getVoteCounts(postId: String): Flow<Map<String, Int>> =
        posts.document(postId).signedInSnapshots(guestSignIn, dispatcher)
            .retryWithBackoff(TAG, resetsBackoff = DocumentSnapshot::isFromServer)
            .map { post -> PostDocument.voteCountsOf(post.get(voteCountsPath)) }
            .flowOn(dispatcher)

    override suspend fun changeVote(change: VoteChange) {
        val uid = accountRepository.loggedInUid.value ?:
            throw VoteException(IllegalStateException("Nobody is logged in"))
        val vote = votesCollectionOf(uid).document(change.postId)
        val post = posts.document(change.postId)
        commit { writeVoteChange(vote, post, change) }
    }

    override suspend fun reclassify(postId: String, animal: String) {
        try {
            posts.document(postId).update(PostDocument.reclassifiedFields(animal)).await()
        } catch (error: FirebaseException) {
            throw VoteException(error)
        }
    }

    private fun votesOf(uid: String): Flow<Map<String, AnimalVote>> =
        votesCollectionOf(uid).signedInSnapshots(guestSignIn, dispatcher)
            .retryWithBackoff(TAG, resetsBackoff = QuerySnapshot::isFromServer)
            .map(::votesIn)
            .flowOn(dispatcher)

    // The well-formed votes of [snapshot], by post ID; the others are left out.
    private fun votesIn(snapshot: QuerySnapshot): Map<String, AnimalVote> {
        val votes = HashMap<String, AnimalVote>(snapshot.size())
        val fields = arrayOfNulls<Any>(voteFieldPaths.size)
        for (document in snapshot.documents) {
            fields.fillFrom(document)
            VoteDocument.toVote(fields)?.let { vote -> votes[document.id] = vote }
        }
        return votes
    }

    // Overwrites this array with [document]'s fields, in VoteDocument.Field order.
    private fun Array<Any?>.fillFrom(document: DocumentSnapshot) {
        for (index in voteFieldPaths.indices) {
            this[index] = document.get(voteFieldPaths[index])
        }
    }

    // Saves what [writes] does, all or none of it.
    private suspend fun commit(writes: WriteBatch.() -> Unit) {
        try {
            firestore.batch().apply(writes).commit().await()
        } catch (error: FirebaseException) {
            throw VoteException(error)
        }
    }

    // Writes the user's new vote, or deletes it, and moves it between the post's counts.
    private fun WriteBatch.writeVoteChange(
        vote: DocumentReference,
        post: DocumentReference,
        change: VoteChange
    ) {
        val newAnimal = change.newAnimal
        if (newAnimal == null) {
            delete(vote)
        } else {
            set(vote, VoteDocument.fieldsOf(AnimalVote(newAnimal, change.round)))
        }
        addToCounts(post, change.countIncrements)
    }

    // Adds each of [increments] to its animal's count. Field paths name the counts, since an
    // animal's name can hold a dot, which a dotted path would split at.
    private fun WriteBatch.addToCounts(post: DocumentReference, increments: Map<String, Int>) {
        val (firstAnimal, firstIncrement) = increments.entries.first()
        val otherFieldsAndValues = increments.entries.drop(1).flatMap { (animal, increment) ->
            listOf(countPathOf(animal), FieldValue.increment(increment.toLong()))
        }
        update(
            post,
            countPathOf(firstAnimal),
            FieldValue.increment(firstIncrement.toLong()),
            *otherFieldsAndValues.toTypedArray()
        )
    }

    private fun countPathOf(animal: String): FieldPath =
        FieldPath.of(PostDocument.Field.VOTE_COUNTS.key, animal)

    private fun votesCollectionOf(uid: String): CollectionReference =
        firestore.collection(ProfileDocument.COLLECTION).document(uid)
            .collection(VoteDocument.COLLECTION)

    private companion object {
        const val TAG = "FirestoreVoteRepo"

        val voteFieldPaths = fieldPathsOf(VoteDocument.Field.entries)

        val voteCountsPath: FieldPath = FieldPath.of(PostDocument.Field.VOTE_COUNTS.key)
    }
}
