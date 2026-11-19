package edu.csuci.tweeter

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import edu.csuci.tweeter.notification.PostNotificationTap
import edu.csuci.tweeter.ui.TweeterApp
import edu.csuci.tweeter.ui.theme.TweeterTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The app's only activity, which shows [TweeterApp]. Tapping a notification about a post opens
 * it, or brings it to the front, and shows the post in Feed.
 */
class MainActivity : ComponentActivity() {
    // Each time a notification about a post is tapped, kept until TweeterApp opens Feed for it,
    // which may be after the tap arrives.
    private val feedOpeningRequests = Channel<Unit>(Channel.CONFLATED)
    private val feedOpenings: Flow<Unit> = feedOpeningRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // The app starts afresh, so no screen it restores can still use a camera photo.
            (application as TweeterApplication).container.cameraFileStore.discardAll()
            // Only now: an activity recreated later gets the same intent, whose post was shown.
            showTappedPost(intent)
        }
        // The app is always light, so the system bars always use dark icons, even when the
        // device is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            TweeterTheme {
                TweeterApp(feedOpenings = feedOpenings)
            }
        }
    }

    // A notification tapped while the activity is open brings it to the front with its intent.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showTappedPost(intent)
    }

    // Asks Feed to show the post whose notification sent [intent], if one did, and opens Feed.
    private fun showTappedPost(intent: Intent) {
        val postId = PostNotificationTap.postIdIn(intent) ?: return
        (application as TweeterApplication).container.showInFeedRequest.send(postId)
        feedOpeningRequests.trySend(Unit)
    }
}
