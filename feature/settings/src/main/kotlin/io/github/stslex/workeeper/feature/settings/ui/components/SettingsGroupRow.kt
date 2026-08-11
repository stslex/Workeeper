// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.fadedOut

/**
 * The mockup's `.srow` (extraction §5.3): min-height 64dp — deliberately NOT the 88dp
 * `--row-h` of the detail screens; two row heights coexist in the design — a top rule on
 * every row (`--hair-s` → `borderSubtle`, the standing no-slot substitution), gap 12dp,
 * title at the body rung in `--max` (or `--rust` → `status.error` for the destructive
 * variant — text colour only, no icon, no container; rust sits on the page surface, the
 * pair the contrast map passes by construction), optional `.meta` sub-line at 4dp.
 *
 * Trailing, in order: an optional `.val` (mono 15/500, `--body`), then a chevron
 * ([RowChevron.InApp] `M9 6l6 6-6 6` / [RowChevron.External] the arrow-out-of-box — §5.3
 * keeps the two destinations visually distinct), or a [content] trailing control slot (`.mseg`,
 * `.sw`) for plain rows. The mockup's hover (`--sec`) maps onto the pressed state, like
 * every v3 control.
 */
@Composable
internal fun SettingsGroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    chevron: RowChevron = RowChevron.None,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    // The trailing control slot — named `content` because it is this row's single
    // composable slot and lint's ComposableLambdaParameterNaming demands the name.
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = AppUi.colors.surfaceTier1.let {
            if (isPressed && onClick != null) it else it.fadedOut()
        },
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "srow-bg",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .let { base ->
                    if (onClick != null) {
                        base.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        base
                    }
                }
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(
                    horizontal = AppDimension.screenEdge,
                    vertical = AppDimension.Space.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppUi.typography.text.body,
                    color = if (destructive) {
                        AppUi.colors.status.error
                    } else {
                        AppUi.colors.textPrimary
                    },
                )
                subtitle?.let {
                    Text(
                        modifier = Modifier.padding(top = AppDimension.Space.xs),
                        text = it,
                        style = AppUi.typography.mono.meta,
                        color = AppUi.colors.textTertiary,
                    )
                }
            }
            value?.let {
                Text(
                    text = it,
                    style = AppUi.typography.mono.body.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = AppUi.colors.textSecondary,
                    maxLines = 1,
                )
            }
            when (chevron) {
                RowChevron.InApp -> RowChevronIcon(icon = AppIcons.ChevronRight)
                RowChevron.External -> RowChevronIcon(icon = AppIcons.ExternalLink)
                RowChevron.None -> Unit
            }
            content?.invoke(this)
        }
    }
}

/** What sits after an interactive row's text: the in-app chevron, the out-of-app arrow, or nothing. */
internal enum class RowChevron { InApp, External, None }

@Composable
private fun RowChevronIcon(icon: ImageVector) {
    Icon(
        modifier = Modifier.size(AppDimension.iconSm),
        imageVector = icon,
        // The row is the semantic unit; its title labels the action.
        contentDescription = null,
        tint = AppUi.colors.textDim,
    )
}

/** `.srow{min-height:64px}` — the settings row's own height (§5.3), `heightXl`'s rung. */
private val ROW_MIN_HEIGHT = AppDimension.heightXl
