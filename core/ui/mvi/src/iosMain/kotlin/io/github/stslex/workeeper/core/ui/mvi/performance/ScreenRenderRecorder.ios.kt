package io.github.stslex.workeeper.core.ui.mvi.performance

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberPlatformScreenRenderRecorder(): ScreenRenderRecorder =
    NoOpScreenRenderRecorder

/** GUARD: iOS render telemetry is explicitly no-op until a real backend exists. */
internal object NoOpScreenRenderRecorder : ScreenRenderRecorder {

    override fun start(screenName: String) = Unit

    override fun stop(screenName: String) = Unit
}
