// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.media

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
actual fun rememberImageSourceLauncher(
    onImagePicked: (ref: String) -> Unit,
    onImagePickCancelled: () -> Unit,
    onCameraPermissionResult: (granted: Boolean) -> Unit,
): ImageSourceLauncher {
    val context = LocalContext.current
    // The camera's result carries no URI, so the target it launched with is kept here.
    // A rotation keeps it (MainActivity handles configuration changes); a process death between
    // launch and result loses it (tech-debt.md, the exercise process-death row).
    val pendingCameraTarget = remember { mutableStateOf<String?>(null) }
    // GUARD: remembered — rememberLauncherForActivityResult keys its registration on the
    // contract instance, so a contract built per recomposition re-registers every time.
    val takePicture = remember { ActivityResultContracts.TakePicture() }
    val pickVisualMedia = remember { ActivityResultContracts.PickVisualMedia() }
    val requestPermission = remember { ActivityResultContracts.RequestPermission() }
    val camera = rememberLauncherForActivityResult(takePicture) { success ->
        val target = pendingCameraTarget.value
        if (success && target != null) onImagePicked(target) else onImagePickCancelled()
        pendingCameraTarget.value = null
    }
    val gallery = rememberLauncherForActivityResult(pickVisualMedia) { uri ->
        if (uri != null) onImagePicked(uri.toString()) else onImagePickCancelled()
    }
    val permission = rememberLauncherForActivityResult(requestPermission) { granted ->
        onCameraPermissionResult(granted)
    }
    return remember(context, camera, gallery, permission) {
        object : ImageSourceLauncher {
            override fun launchCamera(targetRef: String) {
                pendingCameraTarget.value = targetRef
                camera.launch(targetRef.toUri())
            }

            override fun launchGallery() {
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }

            override fun requestCameraPermission() {
                permission.launch(Manifest.permission.CAMERA)
            }

            override fun openAppSettings() {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
