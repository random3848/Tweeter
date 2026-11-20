package edu.csuci.tweeter.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import edu.csuci.tweeter.MainActivity

// The extra of MainActivity's intent naming the post whose notification was tapped.
private const val EXTRA_POST_ID = "edu.csuci.tweeter.extra.POST_ID"

/**
 * What tapping a post's notification does: it opens [MainActivity] with the post's ID, which the
 * activity asks Feed to show.
 */
object PostNotificationTap {
    /**
     * The intent a notification about the post with [postId] sends when tapped. It opens the app,
     * or brings it to the front in the state it was left in, which then shows the post.
     */
    fun pendingIntentFor(context: Context, postId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_POST_ID, postId)
            // Tells this post's intent from another's, which would otherwise replace it.
            .setIdentifier(postId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * The ID of the post whose notification sent [intent], or null if it wasn't one, or if the
     * user opened the app again from Recents, which sends the intent that started it again.
     */
    fun postIdIn(intent: Intent): String? {
        val isFromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        return if (isFromRecents) null else intent.getStringExtra(EXTRA_POST_ID)
    }
}
