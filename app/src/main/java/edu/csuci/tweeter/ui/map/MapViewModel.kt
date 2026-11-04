package edu.csuci.tweeter.ui.map

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.Position

// The key of the map's camera in the SavedStateHandle.
private const val CAMERA_KEY = "camera"

// Where the map opens unless it is coming back to where it was.
private val CAMPUS_CAMERA = CameraPosition(
    center = Position(longitude = CAMPUS_LONGITUDE, latitude = CAMPUS_LATITUDE),
    zoom = CAMPUS_ZOOM
)

/** Where the map's camera moves next. */
sealed interface MapMove {
    /**
     * To the pin of the post with [postId], the post whose popup is open, once it has loaded.
     *
     * @property postId The post the move is for, so that finishing it can't finish a move to a
     *     post selected since.
     */
    data class ToPost(val postId: String) : MapMove

    /** To the user, once their location is known. */
    data object ToUser : MapMove
}

/**
 * What [MapScreen] shows.
 *
 * @property posts Every post, each shown as a pin; empty until the posts load.
 * @property selectedRow The row in [posts] of the post whose popup is open, or null if none is or
 *     it hasn't loaded.
 * @property pendingMove Where the camera moves next, or null if it stays where the user left it.
 * @property pins The pins of [posts], written once per table.
 */
data class MapUiState(
    val posts: PostTable = PostTable.EMPTY,
    val selectedRow: Int? = null,
    val pendingMove: MapMove? = null,
    val pins: MapPins = MapPins(posts)
) {
    /** The selected post's pin alone, drawn over [pins], or no pin if none is selected. */
    val selectedPin: GeoJsonData = GeoJsonData.JsonString(pinJsonOf(posts, selectedRow))

    /** Where the selected post's photo was taken, or null if no post is selected. */
    val selectedPosition: Position? = selectedRow?.let(posts::positionOf)
}

/**
 * The pins of [posts], as the GeoJSON the map draws, written when the table arrives rather than
 * each time the selection changes.
 */
data class MapPins(private val posts: PostTable) {
    /** A pin for each post, identified by the post's ID. */
    val geoJson: GeoJsonData = GeoJsonData.JsonString(pinsJsonOf(posts))
}

/**
 * Holds what [MapScreen] shows: the map, the posts as pins, which post's popup is open, and where
 * the camera moves next.
 *
 * The popup and the move live here, not in [showOnMapRequest], so a request from Feed is handled
 * once: Map opened again shows what the user left, instead of moving back to the requested post.
 *
 * @param postRepository Where the posts come from.
 * @param showOnMapRequest The post Feed asks Map to show, whose popup this opens once.
 * @param showInFeedRequest Where [showInFeed] asks Feed to show a post.
 * @param savedStateHandle Keeps the camera if Android ends the app while it is in the background.
 * @param createMap Creates the map, opening where the camera given to it points.
 */
class MapViewModel internal constructor(
    postRepository: PostRepository,
    private val showOnMapRequest: ShowPostRequest,
    private val showInFeedRequest: ShowPostRequest,
    savedStateHandle: SavedStateHandle,
    createMap: (CameraPosition) -> PostMap
) : ViewModel() {
    private val selection = MutableStateFlow(Selection())

    // The camera saved before Android ended the app, as JSON, or null if there is none.
    private val restoredCamera: String? =
        savedStateHandle.get<Bundle>(CAMERA_KEY)?.getString(CAMERA_KEY)

    private val lazyMap = lazy { createMap(restoredCamera?.let(::decodeCamera) ?: CAMPUS_CAMERA) }

    /**
     * The map, created the first time Map shows it. It opens over the campus, or where it was
     * before Android ended the app, and lives as long as this ViewModel.
     */
    internal val map: PostMap by lazyMap

    /**
     * What the map shows, updated as the posts change and the user taps. Every pin is written once
     * per table; a new selection only finds its post's row and writes that one pin.
     */
    val uiState: StateFlow<MapUiState> = combine(
        postRepository.getPostTable().map(::MapUiState),
        selection
    ) { postsState, selection ->
        postsState.copy(
            selectedRow = selection.postId?.let(postsState.posts::rowOf),
            pendingMove = selection.pendingMove
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MapUiState())

    init {
        savedStateHandle.setSavedStateProvider(CAMERA_KEY) {
            Bundle().apply { putString(CAMERA_KEY, cameraToSave()) }
        }
        viewModelScope.launch {
            showOnMapRequest.postId.filterNotNull().collect { postId ->
                selectPost(postId)
                showOnMapRequest.clear()
            }
        }
    }

    /** Opens the popup of the post with [postId], closing any other, and moves the map to it. */
    fun selectPost(postId: String) {
        selection.value = Selection(postId = postId, pendingMove = MapMove.ToPost(postId))
    }

    /** Closes the popup, and the map no longer moves to its pin if it hasn't yet. */
    fun clearSelection() {
        selection.update { current ->
            val moveToUser = current.pendingMove.takeIf { it == MapMove.ToUser }
            Selection(pendingMove = moveToUser)
        }
    }

    /** Moves the map to the user once their location is known, instead of to a post. */
    fun moveToUser() {
        selection.update { it.copy(pendingMove = MapMove.ToUser) }
    }

    /** Forgets [move] once the map has made it, unless another move has replaced it since. */
    fun finishMove(move: MapMove) {
        selection.update { if (it.pendingMove == move) it.copy(pendingMove = null) else it }
    }

    /** Asks Feed to show the post with [postId], which it scrolls to when it opens. */
    fun showInFeed(postId: String) {
        showInFeedRequest.send(postId)
    }

    override fun onCleared() {
        if (lazyMap.isInitialized()) {
            map.close()
        }
    }

    // Where the map is, or where it was when Android ended the app if Map hasn't shown it since.
    private fun cameraToSave(): String? =
        if (lazyMap.isInitialized()) encodeCamera(map.state.cameraPosition) else restoredCamera

    /**
     * Which post's popup is open, and where the camera moves next. Selecting a post changes both
     * in one step, so the screen never sees the new popup with the previous move.
     */
    private data class Selection(val postId: String? = null, val pendingMove: MapMove? = null)

    companion object {
        /** Creates a [MapViewModel] that reads the app's shared posts and requests. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                MapViewModel(
                    postRepository = application.container.postRepository,
                    showOnMapRequest = application.container.showOnMapRequest,
                    showInFeedRequest = application.container.showInFeedRequest,
                    savedStateHandle = createSavedStateHandle(),
                    createMap = ::PostMap
                )
            }
        }
    }
}

private fun encodeCamera(camera: CameraPosition): String =
    Json.encodeToString(CameraPosition.serializer(), camera)

private fun decodeCamera(json: String): CameraPosition =
    Json.decodeFromString(CameraPosition.serializer(), json)
