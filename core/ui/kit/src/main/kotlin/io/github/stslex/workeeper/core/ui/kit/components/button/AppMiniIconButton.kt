// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The card header's `.mini` icon button (extraction §1.5): **34×34**, an 8dp radius (mockup
 * 9px, rounded onto the ladder), resting tint `dim`, 17dp stroked glyph. The mockup's hover —
 * `color:--body; background:--hair` — maps onto the pressed state, on the `fast` token like
 * the CSS's 140ms transitions. The pressed wash literally *is* `--hair` in the mockup, so it
 * reads [io.github.stslex.workeeper.core.ui.kit.theme.AppColors.borderSubtle] — a border slot
 * used as a wash by the contract's own drawing, noted rather than laundered through a new
 * token.
 *
 * 34dp undershoots the 48dp touch-target guidance; it is the drawn size, three-in-a-row in a
 * card header where 48dp targets would not fit. Same deliberate trade the mockup makes.
 *
 * [glyphRotationDegrees] exists for `.mini.rot` — the expand chevron rotates 90° over
 * `base`/`out` when the card opens; the caller animates the value so the button itself stays
 * stateless.
 */
@Composable
fun AppMiniIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphRotationDegrees: Float = 0f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.borderSubtle else Color.Transparent,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "mini-bg",
    )
    val tint by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.textSecondary else AppUi.colors.textDim,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "mini-tint",
    )
    Box(
        modifier = modifier
            .size(MINI_SIZE)
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier
                .size(MINI_GLYPH_SIZE)
                .rotate(glyphRotationDegrees),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** The mockup's 34×34 — drawn size, deliberately below the 48dp guidance (see KDoc). */
private val MINI_SIZE: Dp = 34.dp

/** 17×17 glyphs at 1.8 stroke (session-v3f L103). */
private val MINI_GLYPH_SIZE: Dp = 17.dp
