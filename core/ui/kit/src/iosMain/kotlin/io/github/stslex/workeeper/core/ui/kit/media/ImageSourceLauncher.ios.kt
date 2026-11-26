// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

interface AppleImageSession : ImageSourceLauncher {
    fun retire()
}

fun interface AppleImagePresenter {
    fun bind(picked: (String) -> Unit, cancelled: () -> Unit, permission: (Boolean) -> Unit): AppleImageSession
}

val LocalAppleImagePresenter = staticCompositionLocalOf<AppleImagePresenter?> { null }

@Composable
actual fun rememberImageSourceLauncher(
    onImagePicked: (ref: String) -> Unit,
    onImagePickCancelled: () -> Unit,
    onCameraPermissionResult: (granted: Boolean) -> Unit,
): ImageSourceLauncher {
    val presenter = LocalAppleImagePresenter.current
    val picked = rememberUpdatedState(onImagePicked)
    val cancelled = rememberUpdatedState(onImagePickCancelled)
    val permission = rememberUpdatedState(onCameraPermissionResult)
    val session = remember(presenter) {
        presenter?.bind({ picked.value(it) }, { cancelled.value() }, { permission.value(it) })
            ?: object : AppleImageSession {
                override fun launchCamera(targetRef: String) = cancelled.value()
                override fun launchGallery() = cancelled.value()
                override fun requestCameraPermission() = permission.value(false)
                override fun openAppSettings() = Unit
                override fun retire() = Unit
            }
    }
    DisposableEffect(session) { onDispose(session::retire) }
    return session
}
