package edu.csuci.tweeter.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.QuerySnapshot

private const val TAG = "PostSnapshots"

private val postFieldPaths = fieldPathsOf(PostDocument.Field.entries)

/**
 * The well-formed posts of this snapshot of [PostDocument]s, in its order, as one table; the others
 * are logged and left out. Only [PostDocument]'s fields are read, into one array reused for every
 * document.
 */
internal fun QuerySnapshot.toPostTable(): PostTable {
    val builder = PostTable.Builder(capacity = size())
    val fields = arrayOfNulls<Any>(postFieldPaths.size)
    for (document in documents) {
        fields.fillFrom(document)
        val post = PostDocument.toPost(document.id, fields)
        if (post == null) {
            Log.w(TAG, "Skipping malformed post ${document.id}")
        } else {
            builder.add(post)
        }
    }
    return builder.build()
}

// Overwrites this array with [document]'s fields, in PostDocument.Field order. A post this phone
// just added has no server time yet; until Firestore sets it, use Firestore's estimate so the
// post shows up right away.
private fun Array<Any?>.fillFrom(document: DocumentSnapshot) {
    for (index in postFieldPaths.indices) {
        this[index] = document.get(postFieldPaths[index], ServerTimestampBehavior.ESTIMATE)
    }
}
