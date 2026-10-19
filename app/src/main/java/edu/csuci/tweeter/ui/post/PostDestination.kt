package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.navigation.TopLevelDestination

/** The bottom-bar destination for creating a photo post. */
object PostDestination : TopLevelDestination {
    override val route = PostRoute
    override val selectedIcon = R.drawable.ic_camera_filled
    override val unselectedIcon = R.drawable.ic_camera_outlined
    override val contentDescription = R.string.create_photo_post
}
