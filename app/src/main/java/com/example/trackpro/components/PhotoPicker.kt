package com.example.trackpro.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.trackpro.TrackProApp

/** Two ways to get a photo: the system photo picker, or the camera. */
class PhotoPicker internal constructor(
    val fromGallery: () -> Unit,
    val fromCamera: () -> Unit,
)

/**
 * Wires the system photo picker and the camera to one callback.
 *
 * Neither needs a runtime permission: the photo picker grants access to exactly the picture
 * chosen, and the camera writes into a file this app hands it through a FileProvider.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val photos = (context.applicationContext as TrackProApp).online.photos
    val callback by rememberUpdatedState(onPicked)

    // Survives the camera app taking over the screen, which can recreate this activity.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(callback)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pendingCapture?.let(Uri::parse)
        pendingCapture = null
        if (saved && target != null) callback(target)
    }

    return remember(gallery, camera) {
        PhotoPicker(
            fromGallery = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            fromCamera = {
                val target = photos.newCameraTarget()
                pendingCapture = target.toString()
                camera.launch(target)
            },
        )
    }
}
