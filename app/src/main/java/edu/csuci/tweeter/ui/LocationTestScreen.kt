package edu.csuci.tweeter.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.ImportPath
import edu.csuci.tweeter.LocationTestUiState
import edu.csuci.tweeter.LocationTestViewModel
import edu.csuci.tweeter.location.LocationChooser
import edu.csuci.tweeter.location.LocationFix
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMBNAIL_PX = 256

/** The photo-location test screen: capture or import a photo, compare locations, save results. */
@Composable
fun LocationTestScreen(viewModel: LocationTestViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.requestDeviceFix() }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCaptureUri
        if (saved && uri != null) {
            viewModel.onPhoto(uri, ImportPath.CAMERA)
        }
        pendingCaptureUri = null
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.onPhoto(it, ImportPath.PHOTO_PICKER) } }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onPhoto(it, ImportPath.FILES) } }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ),
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    val uri = viewModel.newCaptureUri()
                    pendingCaptureUri = uri
                    cameraLauncher.launch(uri)
                }) { Text("Camera") }
                OutlinedButton(onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) { Text("Picker") }
                OutlinedButton(onClick = { filesLauncher.launch(arrayOf("image/*")) }) {
                    Text("Files")
                }
            }
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OsmMap(
                markers = MapMarkers(
                    exif = state.exif?.fix?.point,
                    device = state.device,
                    manual = state.manual?.point,
                    focus = state.choice?.fix?.point ?: state.device?.point,
                ),
                onMapClick = viewModel::onMapTapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            ResultsPanel(
                state = state,
                csvPath = viewModel.csvFile.absolutePath,
                onRefreshFix = viewModel::requestDeviceFix,
                onClearPin = viewModel::clearManualPin,
                onSave = viewModel::saveResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ResultsPanel(
    state: LocationTestUiState,
    csvPath: String,
    onRefreshFix: () -> Unit,
    onClearPin: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.photoUri?.let { PhotoThumbnail(it) }
            Column {
                Text("Source: ${state.importPath?.name ?: "none"}")
                Text("Chosen: ${state.choice?.fix?.let { describe(it) } ?: "—"}")
                Text(state.choice?.reason ?: "Take or import a photo. Tap the map to drop a pin.")
            }
        }
        HorizontalDivider()
        val exif = state.exif
        MonoLine("EXIF (blue)", exif?.fix?.let { describe(it) } ?: exif?.note ?: "—")
        if (exif != null) {
            MonoLine(
                "  opened",
                if (exif.openedOriginal) "original (unredacted)" else "plain URI",
            )
            exif.processingMethod?.let { MonoLine("  method", it) }
            exif.altitudeMeters?.let { MonoLine("  altitude", "%.1f m".format(Locale.US, it)) }
            exif.dateTimeOriginal?.let { MonoLine("  taken", it) }
        }
        MonoLine("Device (green)", state.device?.let { describe(it) } ?: state.deviceStatus)
        state.device?.timeMillis?.let {
            val ageSeconds = (System.currentTimeMillis() - it) / 1000
            MonoLine("  age", "$ageSeconds s (${state.deviceStatus})")
        }
        MonoLine("Pin (red)", state.manual?.let { describe(it) } ?: "tap the map")
        HorizontalDivider()
        MonoLine("EXIF↔device", distanceText(state.exif?.fix, state.device))
        MonoLine("EXIF↔pin", distanceText(state.exif?.fix, state.manual))
        MonoLine("device↔pin", distanceText(state.device, state.manual))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefreshFix) { Text("Refresh GPS") }
            OutlinedButton(onClick = onClearPin, enabled = state.manual != null) {
                Text("Clear pin")
            }
            Button(onClick = onSave, enabled = state.photoUri != null) { Text("Save") }
        }
        Text("CSV: $csvPath", style = MaterialTheme.typography.bodySmall)
        state.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun MonoLine(label: String, value: String) {
    Text(
        "$label: $value",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PhotoThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Photo under test",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp),
        )
    }
}

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = THUMBNAIL_PX.toFloat() / maxOf(info.size.width, info.size.height)
        if (scale < 1f) {
            decoder.setTargetSize(
                (info.size.width * scale).toInt(),
                (info.size.height * scale).toInt(),
            )
        }
    }
}.getOrNull()

private fun describe(fix: LocationFix): String {
    val point = "%.6f, %.6f".format(Locale.US, fix.point.latitude, fix.point.longitude)
    val accuracy = fix.accuracyMeters?.let { " ±%.0f m".format(Locale.US, it) }.orEmpty()
    return point + accuracy
}

private fun distanceText(a: LocationFix?, b: LocationFix?): String {
    if (a == null || b == null) {
        return "—"
    }
    return "%.1f m".format(Locale.US, LocationChooser.haversineMeters(a.point, b.point))
}
