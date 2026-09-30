package edu.csuci.tweeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import edu.csuci.tweeter.ui.LocationTestScreen
import org.maplibre.android.MapLibre

/** Hosts the photo-location test screen. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                LocationTestScreen()
            }
        }
    }
}
