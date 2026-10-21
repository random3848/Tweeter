package edu.csuci.tweeter.ui.map

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

// The key of the map's camera in the SavedStateHandle.
private const val CAMERA_KEY = "camera"

/**
 * Holds the map [MapScreen] shows, with a pin for every post.
 *
 * The map lives as long as this ViewModel, which outlasts the screen when Android recreates the
 * activity, such as on rotation, so the map isn't created and loaded again then.
 *
 * @param postRepository Where the posts come from.
 * @param savedStateHandle Keeps the camera if Android ends the app while it is in the background.
 */
class MapViewModel(postRepository: PostRepository, savedStateHandle: SavedStateHandle) :
    ViewModel() {
    // Every post, newest first; empty until the repository first emits.
    private val posts: StateFlow<List<Post>> = postRepository.getPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The map. It opens over the campus, or where it was before Android ended the app. */
    val mapState: MapState = DefaultMapRuntime.instance.createMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URI),
        cameraPosition = savedStateHandle.savedCamera() ?: CameraPosition(
            center = Position(longitude = CAMPUS_LONGITUDE, latitude = CAMPUS_LATITUDE),
            zoom = CAMPUS_ZOOM
        )
    ) {
        PostPins(posts.collectAsStateWithLifecycle().value)
    }

    init {
        savedStateHandle.setSavedStateProvider(CAMERA_KEY) {
            val camera = Json.encodeToString(CameraPosition.serializer(), mapState.cameraPosition)
            bundleOf(CAMERA_KEY to camera)
        }
    }

    override fun onCleared() {
        mapState.close()
    }

    companion object {
        /** Creates a [MapViewModel] that reads the app's shared [PostRepository]. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                MapViewModel(application.container.postRepository, createSavedStateHandle())
            }
        }
    }
}

/** The camera that [MapViewModel] saved in this handle, or null if it saved none. */
private fun SavedStateHandle.savedCamera(): CameraPosition? =
    get<Bundle>(CAMERA_KEY)?.getString(CAMERA_KEY)?.let {
        Json.decodeFromString(CameraPosition.serializer(), it)
    }
