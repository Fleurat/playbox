package com.example.playbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.playbox.ui.RootScreen
import com.example.playbox.ui.theme.PlayboxTheme

class MainActivity : ComponentActivity() {

    private val app: PlayboxApplication get() = application as PlayboxApplication

    /** READ_MEDIA_VIDEO on Android 13+, the old storage permission below that. */
    private val mediaPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PlayboxTheme {
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { result ->
                    granted = result
                }

                LaunchedEffect(Unit) {
                    if (!granted) launcher.launch(mediaPermission)
                }

                RootScreen(
                    library = app.library,
                    playback = app.playback,
                    hasMediaPermission = granted,
                    onRequestPermission = { launcher.launch(mediaPermission) },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // No media service in this build, so playback should not outlive the UI.
        app.playback.pause()
        app.library.saveMeta()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) app.playback.release()
    }
}
