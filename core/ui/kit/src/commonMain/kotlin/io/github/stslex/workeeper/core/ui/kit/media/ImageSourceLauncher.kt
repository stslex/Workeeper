// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * The platform's camera, photo picker, camera-permission request and app settings. A reference is
 * the platform's string form of the image's location (`ImageRef.value`; a `content://` URI on
 * Android).
 */
@Stable
interface ImageSourceLauncher {

    /** Opens the camera, which writes the photo to [targetRef]. */
    fun launchCamera(targetRef: String)

    /** Opens the photo picker, images only. */
    fun launchGallery()

    fun requestCameraPermission()

    /** Opens this app's page in the system settings. */
    fun openAppSettings()
}

/**
 * [onImagePicked] receives the captured or picked reference; [onImagePickCancelled] fires when
 * the camera or the picker returns without one; [onCameraPermissionResult] reports the request's
 * answer.
 */
@Composable
expect fun rememberImageSourceLauncher(
    onImagePicked: (ref: String) -> Unit,
    onImagePickCancelled: () -> Unit,
    onCameraPermissionResult: (granted: Boolean) -> Unit,
): ImageSourceLauncher
