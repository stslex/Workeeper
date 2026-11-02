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
 * A settings row: top rule, title (destructive = text colour only), optional sub-line, then an
 * optional value, a chevron, or a [content] trailing control. Hover maps onto the pressed state.
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
    // GUARD: must stay named `content` — lint's ComposableLambdaParameterNaming, CI-gated.
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

/** What follows an interactive row's text: in-app chevron, out-of-app arrow, or nothing. */
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

/** The settings row's own height — deliberately not the 88dp detail-screen row. */
private val ROW_MIN_HEIGHT = AppDimension.heightXl
