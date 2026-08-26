package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The base-colour fallback IS the iOS behaviour — there is no AGSL runtime to pretend at. */
@Composable
actual fun Modifier.drawNoiseOrFallback(
    noiseIntensity: Float,
    grainSize: Float,
    baseColor: Color,
    animated: Boolean,
): Modifier = background(baseColor)
