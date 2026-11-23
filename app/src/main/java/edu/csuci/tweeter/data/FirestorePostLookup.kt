package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// How many posts each page of a lookup of many posts holds, such as the posts received while the
// device was offline, or every popular post, so that it comes in responses of bounded size. The
// lookup reads every page.
private const val POSTS_PER_PAGE = 100

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
    // comes back as [timeMillis]. Starting at the next millisecond leaves it out. Each page starts
    // after the last document of the page before, by its exact time and then its ID, so posts
    // received in the same millisecond are neither skipped nor read twice; the time's
    // single-field index serves that order without a composite index.
    override suspend fun postsReceivedAfter(timeMillis: Long): PostTable {
        val timestamp = PostDocument.Field.TIMESTAMP.key
        val query = posts.whereGreaterThanOrEqualTo(timestamp, Timestamp(Date(timeMillis + 1)))
            .orderBy(timestamp, Query.Direction.DESCENDING)
            .limit(POSTS_PER_PAGE.toLong())
        val documents = documentsOnEveryPage(query)
        return withContext(dispatcher) { documents.toPostTable() }
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

    // Each page starts after the last document of the page before, by its like count and then
    // its ID, an order the like count's single-field index serves without a composite index. A
    // post whose likes change between pages can move to a page already read, and be left out, or
    // to one not read yet, and be read again, which is dropped.
    override suspend fun postsWithLikesAtLeast(minLikeCount: Int): PostTable {
        val likeCount = PostDocument.Field.LIKE_COUNT.key
        val query = posts.whereGreaterThanOrEqualTo(likeCount, minLikeCount)
            .orderBy(likeCount, Query.Direction.DESCENDING)
            .limit(POSTS_PER_PAGE.toLong())
        val documents = documentsOnEveryPage(query)
        return withContext(dispatcher) { documents.distinctBy { it.id }.toPostTable() }
    }

    // Every document [query] matches, read POSTS_PER_PAGE at a time; [query] is limited to that.
    private suspend fun documentsOnEveryPage(query: Query): List<DocumentSnapshot> =
        readEveryPage<DocumentSnapshot>(POSTS_PER_PAGE) { lastDocument ->
            val page = if (lastDocument == null) query else query.startAfter(lastDocument)
            lookUp { page.get(Source.SERVER).await() }.documents
        }
}
