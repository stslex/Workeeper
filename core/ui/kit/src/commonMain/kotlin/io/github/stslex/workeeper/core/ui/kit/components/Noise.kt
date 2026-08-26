package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun NoiseBox(
    modifier: Modifier = Modifier,
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .drawNoiseOrFallback(
                noiseIntensity = noiseIntensity,
                grainSize = grainSize,
                baseColor = baseColor,
                animated = animated,
            )
            .padding(paddingValues),
    ) {
        content()
    }
}

@Composable
fun NoiseColumn(
    modifier: Modifier = Modifier,
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .drawNoiseOrFallback(
                noiseIntensity = noiseIntensity,
                grainSize = grainSize,
                baseColor = baseColor,
                animated = animated,
            )
            .padding(paddingValues),
    ) {
        content()
    }
}

/**
 * The platform choice for the noise treatment, hidden behind expect/actual: Android keeps the
 * API-level check, feature flag and AGSL shader; iOS draws the base-colour fallback and does
 * not pretend to implement the shader.
 */
@Composable
expect fun Modifier.drawNoiseOrFallback(
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
): Modifier
