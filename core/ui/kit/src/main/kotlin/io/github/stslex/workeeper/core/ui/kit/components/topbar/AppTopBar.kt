// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.topbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.fadedOut

/**
 * The v3 `.topbar`: a 48dp-rung row of hanging icon buttons and an optional title in one of two
 * sizes ([smallTitle]), dimmed for placeholders. See the screen-extraction spec §1.2.
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    smallTitle: Boolean = false,
    titleDimmed: Boolean = false,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = AppDimension.heightLg)
            .padding(
                horizontal = AppDimension.Space.xxs,
                vertical = AppDimension.Space.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation()
        if (title != null) {
            val style = if (smallTitle) {
                AppUi.typography.text.body.copy(fontWeight = FontWeight.SemiBold)
            } else {
                AppUi.typography.text.section
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimension.Space.xs),
                text = title,
                style = style,
                color = if (titleDimmed) AppUi.colors.textDim else AppUi.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        actions()
    }
}

/**
 * The v3 `.icon-btn`: 48dp target, 21dp glyph, `textTertiary` at rest, the mockup's hover mapped
 * onto the pressed state. Its drawn 12px radius rounds DOWN to `Radius.small`.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: Dp = TOPBAR_GLYPH_SIZE,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = AppUi.colors.surfaceTier1.let { if (isPressed) it else it.fadedOut() },
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "icon-btn-bg",
    )
    val tint by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.textPrimary else AppUi.colors.textTertiary,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "icon-btn-tint",
    )
    Box(
        modifier = modifier
            .size(AppDimension.iconXl)
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
            modifier = Modifier.size(glyphSize),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** The mockup's 21×21 top-bar glyph — a component treatment, kept literal like stroke widths. */
private val TOPBAR_GLYPH_SIZE: Dp = 21.dp
