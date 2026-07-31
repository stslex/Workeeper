// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The drawn press treatment: `transform:scale(.985)` on `fast` + `spring`.
 *
 * `.btn:active{transform:scale(.985)}` with `transition:transform var(--d-fast) var(--e-spring)`
 * — the mockup's only press affordance, and the app's existing idiom (`AppCheckmarkButton` runs
 * the same shape at `.mark:active`'s own `scale(.9)`).
 *
 * **This exists because a ripple is not drawn anywhere and was shipping anyway.** Compose's
 * `clickable` defaults to `LocalIndication`, which under a Material theme is a ripple; six sites
 * in this app already pass `indication = null`, so a ripple was the divergence rather than the
 * convention. Removing it leaves a surface with no press feedback at all, and this is what the
 * drawing puts there instead.
 *
 * **The value is borrowed, and the borrowing is the part to check before reusing this.** `.nb` and
 * `.tabs` have **no `:active` rule drawn at all** — 0.985 comes from `.btn`, which is a 56px
 * full-width dock button where it resolves to ~2.5px of travel. On a 129dp nav item it is ~1.9px
 * and on a 48dp control it would be ~0.7px, so the same coefficient does not carry the same
 * *legibility* down the size range. It is deliberately subtle here: on both surfaces the real
 * feedback is the indicator arriving, and this only has to say the touch landed.
 *
 * @param interactionSource the same source passed to `clickable`, or the scale never fires.
 * @return a scale to hand to `graphicsLayer`, not a `Modifier` — the caller decides which element
 *  it applies to, which differs by surface (the nav bar scales the item box, matching `.btn`'s
 *  whole-button scale; a component with its own background may want only its content).
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESSED_SCALE,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = AppUi.motion.fast,
            easing = AppUi.motion.spring,
        ),
        label = "press-scale",
    )
}

/** `.btn:active{transform:scale(.985)}`. */
const val PRESSED_SCALE = 0.985f
