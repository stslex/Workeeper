package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalAppUiFeatures = staticCompositionLocalOf { appUiFeaturesState() }

@Immutable
data class AppUiFeatures(
    val enableNoise: Boolean,
    val enableBlur: Boolean,
)

fun appUiFeaturesState(
    enableNoise: Boolean = true,
    blurEnable: Boolean = false,
): AppUiFeatures = AppUiFeatures(
    enableNoise = enableNoise,
    enableBlur = blurEnable,
)
