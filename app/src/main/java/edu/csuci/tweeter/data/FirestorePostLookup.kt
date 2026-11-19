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

// The most posts [FirestorePostLookup.postsReceivedAfter] reads at once, so that a device that was
// offline for long doesn't read every post shared meanwhile.
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

    override suspend fun newestPostMillis(): Long? {
        val query = posts.orderBy(PostDocument.Field.TIMESTAMP.key, Query.Direction.DESCENDING)
            .limit(1)
        val newestPost = readPosts(query)
        return if (newestPost.size == 0) null else newestPost.timestampMillis(0)
    }

    // Firestore keeps times to the microsecond, and PostTable to the millisecond, so a post
    // received during millisecond [timeMillis] is matched by its first microsecond or later and
    // comes back as [timeMillis]. Starting at the next millisecond leaves it out.
    override suspend fun postsReceivedAfter(timeMillis: Long): PostTable {
        val timestamp = PostDocument.Field.TIMESTAMP.key
        val query = posts.whereGreaterThanOrEqualTo(timestamp, Timestamp(Date(timeMillis + 1)))
            .orderBy(timestamp, Query.Direction.DESCENDING)
            .limit(MAX_POSTS_PER_LOOKUP)
        return readPosts(query)
    }

    override suspend fun countPostsOfAnimal(animal: String, maxCount: Int): Int {
        val query = posts.whereEqualTo(PostDocument.Field.ANIMAL_TYPE.key, animal)
            .limit(maxCount.toLong())
            .count()
        return lookUp { query.get(AggregateSource.SERVER).await() }.count.toInt()
    }

    // Feed asks this once its post table arrived, so someone, a guest at least, is signed in.
    override suspend fun holdsPost(postId: String): Boolean {
        val post = posts.document(postId)
        return lookUp { post.get(Source.SERVER).await() }.exists()
    }

    private suspend fun readPosts(query: Query): PostTable {
        val snapshot = lookUp { query.get(Source.SERVER).await() }
        return withContext(dispatcher) { snapshot.toPostTable() }
    }

    private suspend fun <T> lookUp(read: suspend () -> T): T {
        try {
            return read()
        } catch (error: FirebaseException) {
            throw PostLookupException(error)
        }
    }
}
