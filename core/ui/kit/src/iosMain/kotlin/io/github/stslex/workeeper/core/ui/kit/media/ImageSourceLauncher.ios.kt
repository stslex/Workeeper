// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.media

import androidx.compose.runtime.Composable

/**
 * Inert: no iOS host exists yet, and a throwaway iosApp stays forbidden. The permanent iOS host
 * implements the camera, the photo picker and the settings link here. Nothing calls back, so a
 * Store stays in the state it wrote before asking.
 */
@Composable
actual fun rememberImageSourceLauncher(
    onImagePicked: (ref: String) -> Unit,
    onImagePickCancelled: () -> Unit,
    onCameraPermissionResult: (granted: Boolean) -> Unit,
): ImageSourceLauncher = InertImageSourceLauncher

private object InertImageSourceLauncher : ImageSourceLauncher {
    override fun launchCamera(targetRef: String) = Unit
    override fun launchGallery() = Unit
    override fun requestCameraPermission() = Unit
    override fun openAppSettings() = Unit
}
