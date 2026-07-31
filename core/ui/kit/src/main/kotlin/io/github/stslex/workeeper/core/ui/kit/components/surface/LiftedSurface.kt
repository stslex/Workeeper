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
 * The v3 **lifted surface** — the mockups' `--slabtop`, and the app's entire elevation
 * vocabulary.
 *
 * ```
 * dark   --slabtop: inset 0 1px 0 rgba(255,255,255,.055);
 * light  --slabtop: 0 1px 3px rgba(13,17,20,.07), 0 6px 18px rgba(13,17,20,.05);
 * ```
 *
 * A lifted surface is `--slab` **plus** `--slabtop` — the tone and the edge treatment are one
 * signature, never half of one, which is why this modifier paints the fill as well. Four things
 * in the mockups carry it: the active exercise card (`session-v3f.html:66`), the open past-session
 * card (`pass2d.html:92`), the chart's tab indicator (`pass2d.html:137`) and the segmented
 * control's thumb (`pass2d.html:167`).
 *
 * ## The mechanism inverts by theme, and that is the design
 *
 * This is the same pattern as `MoltenAccent`: one meaning, two mechanisms, because the two
 * themes have opposite physics.
 *
 * - **Dark** draws a 1dp highlight along the *inside* of the top edge. A cast shadow is
 *   unavailable here: `base` is `#0B0D0F`, so there is no darker colour to cast onto and a drop
 *   shadow on a near-black page is invisible. What a raised object does have on a dark page is a
 *   lit top edge, and that is what is drawn.
 * - **Light** casts a real shadow. The inverse argument holds exactly: on `slab` `#FFFFFF` a
 *   white top-edge highlight is invisible by construction, while a shadow has a page to fall on.
 *
 * **Do not unify them.** The next reader's instinct will be that two branches are one branch too
 * many; they are not interchangeable, and collapsing to either one alone makes the treatment
 * disappear in the other theme rather than merely look different.
 *
 * ## Why this is its own role rather than a level of `AppElevation`
 *
 * Judged against how the four consumers read it. They read it as one *binary* fact — "this
 * surface is lifted" — applied to a shape. None of them picks a level, and there is no ladder to
 * pick from: the mockups have exactly one lift. `AppElevation.level0..level5` is a map from level
 * to *tone*, and its `shadow` is a single global `Dp` that every consumer would share; expressing
 * lift as a level would invent a ladder that does not exist and would still leave the two
 * mechanisms with nowhere to live.
 *
 * So the split is: the **numbers** are theme tokens on `AppElevation` (which is already built
 * from the palette, by `provideAppElevation(colors)`, and is the honest home for a depth value),
 * and the **treatment** is this modifier. `AppElevation.shadow` stays `0.dp` and untouched — it
 * is the claim "nothing in this app floats", which remains true of everything that is not lifted.
 *
 * ## Not the same thing as [AppActiveSurface]
 *
 * [AppActiveSurface] is a *semantic* invariant — exactly one element in the app may say "this is
 * what is being done now" — and `ActiveSurfaceSingleReaderRule` holds it to one call site. Lift
 * is the *mechanism*, and it legitimately has four. A tab indicator is lifted and is not the
 * active surface. [AppActiveSurface] is therefore built out of this modifier rather than the
 * other way round.
 *
 * @param shape the surface's own shape; the shadow's outline and the highlight's clip both come
 *  from it, so passing a shape that disagrees with the caller's `clip` will show at the corners.
 * @param lifted whether the surface is lifted right now. Always call this modifier and drive it
 *  with the flag rather than branching at the call site — the modifier graph then stays stable
 *  across the flip and the transition can animate.
 * @param restingColor what the surface paints when it is not lifted. `surfaceTier1` is the
 *  mockups' resting card and unselected segment, so it is the default.
 */
/**
 * A fully transparent resting colour is repaired to **the lifted colour, faded out**.
 *
 * `Color.Transparent` is the honest way for a caller to say "this surface paints nothing of its
 * own", and a caller saying that should not have to know the fill is animated. Why that endpoint is
 * wrong — transparent is transparent *black*, and the tween carries hue — is [fadedOut]'s KDoc and
 * `FadeToTransparentRule`'s; the declaration is accepted and corrected here, at the one place that
 * knows a tween is involved.
 *
 * The repair target is the **lifted** colour rather than the surface behind, and that is the point:
 * a component cannot know what it is sitting on, and does not need to. Fading `surfaceTier2` out
 * moves alpha only, so no mid-frame is a colour neither endpoint contains, whatever is underneath.
 *
 * Any alpha but zero passes through untouched — a caller who chose a translucent tint chose its
 * hue too.
 *
 * Pure and `internal` so it can be asserted directly: no golden can see a mid-transition frame
 * (§27, "a golden image gates only what a single static frame contains").
 */
internal fun restingFill(restingColor: Color, lifted: Color): Color =
    if (restingColor.alpha == 0f) lifted.fadedOut() else restingColor

@Composable
fun Modifier.liftedSurface(
    shape: Shape,
    lifted: Boolean = true,
    restingColor: Color = AppUi.colors.surfaceTier1,
): Modifier {
    val colors = AppUi.colors
    val elevation = AppUi.elevation
    val motion = AppUi.motion

    // Every value here is a COLOUR or a bounded magnitude, so all three are driven by `out`.
    // `spring` overshoots past 1.0 and would extrapolate a colour lerp or a negative elevation.
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
        // `graphicsLayer` rather than `Modifier.shadow`: the latter compiles to nothing at
        // `0.dp`, so the dark theme — and every unlifted state — would add and remove a node on
        // each flip. This is one node whose elevation happens to be zero.
        .graphicsLayer {
            shadowElevation = shadow.toPx()
            this.shape = shape
            // The shadow is drawn OUTSIDE the surface's bounds. Clipping to the shape here
            // would remove exactly the part that is the effect.
            clip = false
            ambientShadowColor = shadowColor
            spotShadowColor = shadowColor
        }
        .background(color = surface, shape = shape)
        .topEdgeHighlight(shape = shape, color = highlight)
}

/**
 * The dark half: `inset 0 1px 0`, i.e. a 1dp band along the top, clipped to the surface's own
 * outline so it follows the corner radius in and stops.
 *
 * Drawn with `onDrawBehind` and placed after the fill, which reproduces CSS's own order — an
 * inset shadow paints above the background and below the content. It matters less than it
 * sounds (a 1dp band at the very top edge sits under padding on every consumer), but getting the
 * order right costs nothing and removes a question.
 */
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

/** `inset 0 1px 0` — the highlight is one dp tall, in both the mockup and here. */
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
