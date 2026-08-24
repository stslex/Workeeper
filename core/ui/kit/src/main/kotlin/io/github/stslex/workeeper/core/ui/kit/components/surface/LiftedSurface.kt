// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.surface

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.fadedOut

/**
 * Repairs a fully transparent [restingColor] to the lifted colour faded out; any other alpha
 * passes through. See documentation/design-system.md.
 */
internal fun restingFill(restingColor: Color, lifted: Color): Color =
    if (restingColor.alpha == 0f) lifted.fadedOut() else restingColor

/**
 * The v3 lifted surface (`--slabtop`): fill plus edge treatment in one modifier. Dark draws a
 * top-edge highlight, light casts a shadow — see documentation/design-system.md.
 */
@Composable
fun Modifier.liftedSurface(
    shape: Shape,
    lifted: Boolean = true,
    restingColor: Color = AppUi.colors.surfaceTier1,
): Modifier {
    val colors = AppUi.colors
    val elevation = AppUi.elevation
    val motion = AppUi.motion

    // All three ride `out`: `spring` overshoots 1.0 and would extrapolate colour and elevation.
    val surface by animateColorAsState(
        targetValue = if (lifted) colors.surfaceTier2 else restingFill(restingColor, colors.surfaceTier2),
        animationSpec = tween(durationMillis = motion.base, easing = motion.out),
        label = "liftedSurface-fill",
    )
    val highlight by animateColorAsState(
        targetValue = elevation.liftHighlight.let { if (lifted) it else it.fadedOut() },
        animationSpec = tween(durationMillis = motion.base, easing = motion.out),
        label = "liftedSurface-highlight",
    )
    val shadow by animateDpAsState(
        targetValue = if (lifted) elevation.liftShadow else NO_SHADOW,
        animationSpec = tween(durationMillis = motion.base, easing = motion.out),
        label = "liftedSurface-shadow",
    )
    val shadowColor = elevation.liftShadowColor

    return this
        // `graphicsLayer`, not `Modifier.shadow`: the latter compiles to nothing at `0.dp`, so a
        // node would be added and removed on every flip.
        .graphicsLayer {
            shadowElevation = shadow.toPx()
            this.shape = shape
            // GUARD: the shadow draws outside the bounds; clipping to `shape` here erases it.
            clip = false
            ambientShadowColor = shadowColor
            spotShadowColor = shadowColor
        }
        .background(color = surface, shape = shape)
        .topEdgeHighlight(shape = shape, color = highlight)
}

/** Dark half of the lift: a 1dp top-edge band, clipped to the surface outline. */
private fun Modifier.topEdgeHighlight(shape: Shape, color: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val thickness = HIGHLIGHT_THICKNESS.toPx()
    onDrawBehind {
        if (color.alpha > 0f) {
            clipPath(path) {
                drawRect(color = color, size = Size(width = size.width, height = thickness))
            }
        }
    }
}

/** `inset 0 1px 0` — one dp tall, as drawn. */
private val HIGHLIGHT_THICKNESS: Dp = 1.dp

private val NO_SHADOW: Dp = 0.dp

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LiftedSurfacePreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.xl),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                AppDimension.Space.xl,
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT)
                    .liftedSurface(shape = AppUi.shapes.medium, lifted = false)
                    .padding(AppDimension.Space.lg),
            ) {
                Text(
                    text = "resting",
                    style = AppUi.typography.text.body,
                    color = AppUi.colors.textSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .size(width = PREVIEW_WIDTH, height = PREVIEW_HEIGHT)
                    .liftedSurface(shape = AppUi.shapes.medium, lifted = true)
                    .padding(AppDimension.Space.lg),
            ) {
                Text(
                    text = "lifted",
                    style = AppUi.typography.text.body,
                    color = AppUi.colors.textPrimary,
                )
            }
        }
    }
}

private val PREVIEW_WIDTH: Dp = 240.dp
private val PREVIEW_HEIGHT: Dp = 72.dp
