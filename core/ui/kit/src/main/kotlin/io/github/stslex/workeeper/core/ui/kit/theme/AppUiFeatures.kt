package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalAppUiFeatures = staticCompositionLocalOf { appUiFeaturesState() }

data class AppUiFeatures(
    val enableNoise: Boolean,
    val enableBlur: Boolean,
    val defaultAnimationDuration: Int,
)

fun appUiFeaturesState(
    enableNoise: Boolean = true,
    blurEnable: Boolean = false,
    defaultAnimationDuration: Int = 600,
): AppUiFeatures = AppUiFeatures(
    enableNoise = enableNoise,
    enableBlur = blurEnable,
    defaultAnimationDuration = defaultAnimationDuration,
)
