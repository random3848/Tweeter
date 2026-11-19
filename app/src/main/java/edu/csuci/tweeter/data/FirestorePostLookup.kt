package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// The most posts [FirestorePostLookup.postsReceivedAfter] reads at once, so that a check doesn't
// read every post shared over a long time: since a device that was offline for long last checked,
// or within the days a post can become popular in.
private const val MAX_POSTS_PER_LOOKUP = 100L

/**
 * A [PostLookup] that asks Cloud Firestore's server, never its cache on the device, which a
 * background check would otherwise read from and miss new posts.
 *
 * It doesn't sign anyone in: it is for checks that run for a logged-in user, whom Firebase
 * Authentication keeps signed in between runs of the app. Posts are read as described by
 * [PostDocument], and parsed on [dispatcher]. Firestore's failures are thrown as
 * [PostLookupException]s.
 */
class FirestorePostLookup(
    firestore: FirebaseFirestore,
    private val dispatcher: CoroutineDispatcher
) : PostLookup {
    private val posts = firestore.collection(PostDocument.COLLECTION)

    // Firestore keeps times to the microsecond, and PostTable to the millisecond, so a post
    // received during millisecond [timeMillis] is matched by its first microsecond or later and
    // comes back as [timeMillis]. Starting at the next millisecond leaves it out.
    override suspend fun postsReceivedAfter(timeMillis: Long): PostTable {
        val timestamp = PostDocument.Field.TIMESTAMP.key
        val query = posts.whereGreaterThanOrEqualTo(timestamp, Timestamp(Date(timeMillis + 1)))
            .orderBy(timestamp, Query.Direction.DESCENDING)
            .limit(MAX_POSTS_PER_LOOKUP)
        val snapshot = lookUp { query.get(Source.SERVER).await() }
        return withContext(dispatcher) { snapshot.toPostTable() }
    }

    override suspend fun countPostsOfAnimal(animal: String): Int {
        val query = posts.whereEqualTo(PostDocument.Field.ANIMAL_TYPE.key, animal).count()
        return lookUp { query.get(AggregateSource.SERVER).await() }.count.toInt()
    }

    private suspend fun <T> lookUp(read: suspend () -> T): T {
        try {
            return read()
        } catch (error: FirebaseException) {
            throw PostLookupException(error)
        }
    }
}
