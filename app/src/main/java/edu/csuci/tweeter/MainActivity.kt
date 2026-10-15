package edu.csuci.tweeter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import edu.csuci.tweeter.ui.theme.TweeterTheme
import java.io.File

/** The app's only activity. It opens to an empty screen; the app has no screens yet. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light, so the system bars always use dark icons, even when the
        // device is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            TweeterTheme {
                // Screens go here.
                CameraScreen()
            }
        }
    }
}

/** Opens the camera and reports whether the photo was saved. */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var status by rememberSaveable {
        mutableStateOf("No photo taken yet")
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        status = if (success) {
            "Photo saved!"
        } else {
            "Photo cancelled or not saved"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                try {
                    // Create the photo inside the folder from file_paths.xml.
                    val photoDirectory = File(context.cacheDir, "photos")
                    check(photoDirectory.exists() || photoDirectory.mkdirs()) {
                        "Could not create photo folder"
                    }
                    val photoFile = File.createTempFile(
                        "photo_",
                        ".jpg",
                        photoDirectory
                    )
                    // Create an address the camera can use to save the photo.
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    cameraLauncher.launch(uri)
                } catch (error: Exception) {
                    status = "Could not open camera: ${error.message}"
                }
            }
        ) {
            Text("Take Picture")
        }
        Text(
            text = status,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
