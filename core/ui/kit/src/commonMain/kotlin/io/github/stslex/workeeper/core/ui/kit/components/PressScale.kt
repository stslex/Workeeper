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
 * The drawn press treatment, `scale(.985)` on `fast` + `spring`. Returned as a scale rather than
 * a Modifier so each surface picks the element it applies to; pass `clickable`'s own source.
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
