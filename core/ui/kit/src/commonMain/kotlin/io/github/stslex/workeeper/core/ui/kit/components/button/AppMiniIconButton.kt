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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.fadedOut

/**
 * The card header's small icon button. [glyphRotationDegrees] is animated by the caller (the
 * expand chevron) so the button itself stays stateless.
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
        targetValue = AppUi.colors.borderSubtle.let { if (isPressed) it else it.fadedOut() },
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

/** Deliberately below the 48dp touch-target guidance: three of these fit a card header. */
private val MINI_SIZE: Dp = 34.dp

private val MINI_GLYPH_SIZE: Dp = 17.dp
