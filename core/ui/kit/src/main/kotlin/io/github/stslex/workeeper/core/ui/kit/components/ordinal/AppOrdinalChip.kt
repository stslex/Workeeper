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
 * The session card's `.ordchip` — a 24dp state chip that *is* the card's state indicator
 * (extraction §1.5). Seven variants, resolved with the mockup stylesheet's own precedence
 * (session-v3f.html L86–96; later rules win):
 *
 * | state | background | content | border |
 * |---|---|---|---|
 * | resting | — | `dim` number | — |
 * | active (open card) | `max` | `base` number, 500 | — |
 * | fin | **`donefill`** | `meta` **checkmark** | — |
 * | skip | — | `dim` number | dashed `hair-s` (→ `borderDefault`) |
 * | temp (one-off) | — | `dim` number | dashed `dim` |
 * | temp.active | — | `max` number, 500 | dashed `max` |
 * | temp.fin | — | `meta` **checkmark** | dashed `meta` |
 *
 * Two rules that are easy to get wrong, both verified against the stylesheet:
 * - **`fin` always swaps the number for the checkmark** — `.card.fin .ordchip svg` has no
 *   `:not(.temp)` guard, so a finished one-off shows a `meta` check inside a dashed `meta`
 *   chip. (The extraction's own table says "number" there; the stylesheet disagrees and the
 *   stylesheet is the mockup.)
 * - a skipped one-off keeps the `temp` chip (dashed `dim`) — `.temp` is declared after
 *   `.skip`, so the one-off treatment wins the chip while `.skip` wins the card.
 *
 * "Done" for an exercise is this chip plus the title's `meta`/500 treatment — **not** an
 * opacity change, which is precisely the step-5 defect this component replaces.
 *
 * `isActive` is the mockup's `.card.active`, i.e. the card is **open** — not "this is the
 * current exercise". The two usually coincide (the automaton opens the current card), but a
 * manually-opened finished card is active-without-being-current, and it lifts and re-chips
 * exactly like the mockup's.
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
