// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import io.github.stslex.workeeper.App
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.app.ios.resources.Res
import io.github.stslex.workeeper.app.ios.resources.ios_recovery_failed
import io.github.stslex.workeeper.app.ios.resources.ios_recovery_requested
import io.github.stslex.workeeper.app.ios.resources.ios_recovery_retry
import io.github.stslex.workeeper.app.ios.resources.ios_recovery_retry_failed
import io.github.stslex.workeeper.app.ios.resources.ios_recovery_title
import io.github.stslex.workeeper.app.ios.resources.workeeper_mark
import io.github.stslex.workeeper.core.ui.kit.media.LocalAppleImagePresenter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

class WorkeeperHost(media: AppleMediaServices) {

    private val runtime = IosRuntime(IosImages(media), media::cameraGranted)
    private val presenter = AppleMediaPresenter(media, runtime::currentId)

    fun makeController(): UIViewController {
        runtime.start()
        return ComposeUIViewController {
            CompositionLocalProvider(LocalAppleImagePresenter provides presenter) { WorkeeperRoot(runtime) }
        }
    }
}

@Composable
internal fun WorkeeperRoot(runtime: IosRuntime) {
    val error by runtime.problem.collectAsState()
    if (error != null) {
        Column(Modifier.safeDrawingPadding()) {
            Image(painterResource(Res.drawable.workeeper_mark), contentDescription = null)
            Text(stringResource(Res.string.ios_recovery_title))
            val message = when (error) {
                IosHostProblem.RecoveryRequested -> Res.string.ios_recovery_requested
                IosHostProblem.RetryFailed -> Res.string.ios_recovery_retry_failed
                else -> Res.string.ios_recovery_failed
            }
            Text(stringResource(message))
            Button(onClick = { runtime.retry() }) { Text(stringResource(Res.string.ios_recovery_retry)) }
        }
    } else {
        val phase by runtime.appUiPhases.collectAsState()
        val id = (phase as? AppUiPhase.Generation)?.id
        App(runtime, runtime::resolve) { runtime.openRecovery(id) }
    }
}
