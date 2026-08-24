// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.ordinal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.border.dashedBorder
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The session card's 24dp `.ordchip` state indicator. `isActive` is the card being open, not
 * "this is the current exercise". See documentation/feature-specs/screen-extraction.md §1.5.
 */
@Composable
fun AppOrdinalChip(
    ordinal: Int,
    isActive: Boolean,
    isDone: Boolean,
    isSkipped: Boolean,
    isOneOff: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppUi.colors
    val background: Color
    val content: Color
    val border: Color
    val emphasized: Boolean
    when {
        isOneOff && isDone -> {
            background = Color.Transparent
            content = colors.textTertiary
            border = colors.textTertiary
            emphasized = false
        }

        isOneOff && isActive -> {
            background = Color.Transparent
            content = colors.textPrimary
            border = colors.textPrimary
            emphasized = true
        }

        isOneOff -> {
            background = Color.Transparent
            content = colors.textDim
            border = colors.textDim
            emphasized = false
        }

        isSkipped -> {
            background = Color.Transparent
            content = colors.textDim
            border = colors.borderDefault
            emphasized = false
        }

        isDone -> {
            background = colors.donefill
            content = colors.textTertiary
            border = Color.Transparent
            emphasized = false
        }

        isActive -> {
            background = colors.accent
            content = colors.onAccent
            border = Color.Transparent
            emphasized = true
        }

        else -> {
            background = Color.Transparent
            content = colors.textDim
            border = Color.Transparent
            emphasized = false
        }
    }
    val animatedBackground by animateColorAsState(
        targetValue = background,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "ordchip-bg",
    )
    val animatedContent by animateColorAsState(
        targetValue = content,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "ordchip-content",
    )
    Box(
        modifier = modifier
            .size(CHIP_SIZE)
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .background(animatedBackground)
            .dashedBorder(color = border, cornerRadius = AppDimension.Radius.small),
        contentAlignment = Alignment.Center,
    ) {
        if (isDone) {
            Icon(
                modifier = Modifier.size(CHECK_SIZE),
                imageVector = AppIcons.OrdinalCheck,
                contentDescription = null,
                tint = animatedContent,
            )
        } else {
            Text(
                text = ordinal.toString(),
                style = AppUi.typography.mono.caption.copy(
                    fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
                ),
                color = animatedContent,
            )
        }
    }
}

/** 24×24 in the mockup. Numerically `iconMd`, but this is a chip, not an icon slot. */
private val CHIP_SIZE: Dp = 24.dp

/** The check renders at 13×13 inside the chip (session-v3f L94). */
private val CHECK_SIZE: Dp = 13.dp

@Preview
@Composable
private fun AppOrdinalChipPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Row {
            AppOrdinalChip(ordinal = 1, isActive = false, isDone = false, isSkipped = false, isOneOff = false)
            AppOrdinalChip(ordinal = 2, isActive = true, isDone = false, isSkipped = false, isOneOff = false)
            AppOrdinalChip(ordinal = 3, isActive = false, isDone = true, isSkipped = false, isOneOff = false)
            AppOrdinalChip(ordinal = 4, isActive = false, isDone = false, isSkipped = true, isOneOff = false)
            AppOrdinalChip(ordinal = 5, isActive = false, isDone = false, isSkipped = false, isOneOff = true)
            AppOrdinalChip(ordinal = 6, isActive = true, isDone = false, isSkipped = false, isOneOff = true)
            AppOrdinalChip(ordinal = 7, isActive = false, isDone = true, isSkipped = false, isOneOff = true)
        }
    }
}
