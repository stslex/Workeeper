// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The mockup's `.exhead` (extraction §4.2) — the exercise switcher, moved out of the topbar:
 * "заголовок крупный, стрелка ровно одна — назад". The whole row is one button opening the
 * picker sheet; the 26-rung title carries the exercise name and the trailing `.swap` tile
 * holds a chevron-down.
 *
 * Geometry, derived (§0.2): title 24px → `text.title` (26 rung; its declared −.02em tracking
 * resolved to the rung's −0.39sp by B4 — the mockup's value was chosen against a UA font).
 * Tile 34px → 32dp (`Icon.big`), radius 11px → 8dp — no 12 rung, rounded down exactly as
 * `AppIconButton` rounds the same mockup value (its KDoc carries the argument). Gap 11px →
 * 12dp (`Space.md`); row padding `0 gutter 4px` → horizontal `screenEdge`, bottom `xs`.
 *
 * The mockup's hover — `.exhead:hover .swap{background:var(--raise)}` — maps onto the
 * pressed state, animated on the `fast` token like `AppIconButton`'s: no hover on touch
 * hardware, and a Material ripple would be a different treatment than the one drawn.
 */
@Composable
internal fun ExerciseHeader(
    name: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tileBackground by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.surfaceTier4 else AppUi.colors.surfaceTier1,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "exhead-swap-bg",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = actionLabel,
                onClick = onClick,
            )
            .padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                bottom = AppDimension.Space.xs,
            )
            .testTag("ExerciseChartPickerOpen"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(end = AppDimension.Space.md),
            text = name,
            style = AppUi.typography.text.title,
            color = AppUi.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(AppDimension.Icon.big)
                .clip(RoundedCornerShape(AppDimension.Radius.small))
                .background(tileBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.Icon.small),
                imageVector = AppIcons.ChevronDown,
                // The row is one semantic button; the title is its label and this glyph is
                // decoration — `.swap` draws no text and the mockup's hint names the intent.
                contentDescription = null,
                tint = AppUi.colors.textSecondary,
            )
        }
    }
}

@Preview
@Composable
private fun ExerciseHeaderLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            ExerciseHeader(name = "разведение ног", actionLabel = "Сменить упражнение", onClick = {})
        }
    }
}

@Preview
@Composable
private fun ExerciseHeaderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            ExerciseHeader(name = "разведение ног", actionLabel = "Сменить упражнение", onClick = {})
        }
    }
}
