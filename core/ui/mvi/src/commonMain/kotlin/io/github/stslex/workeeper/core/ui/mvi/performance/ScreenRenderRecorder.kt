package io.github.stslex.workeeper.core.ui.mvi.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

internal interface ScreenRenderRecorder {

    fun start(screenName: String)

    fun stop(screenName: String)
}

// GUARD: composition-scoped override; a test fake must not escape through process-global state.
internal val LocalScreenRenderRecorder: ProvidableCompositionLocal<ScreenRenderRecorder?> =
    staticCompositionLocalOf { null }

@Composable
internal fun rememberScreenRenderRecorder(): ScreenRenderRecorder {
    // GUARD: both reads happen unconditionally, so the composition group structure cannot depend
    // on whether an override is present.
    val platform = rememberPlatformScreenRenderRecorder()
    val override = LocalScreenRenderRecorder.current
    return override ?: platform
}

@Composable
internal expect fun rememberPlatformScreenRenderRecorder(): ScreenRenderRecorder
