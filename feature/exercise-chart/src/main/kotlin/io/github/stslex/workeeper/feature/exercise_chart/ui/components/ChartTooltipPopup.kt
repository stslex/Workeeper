// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel

private val TooltipAnchorGap = AppDimension.Space.sm
private val TooltipEdgeMargin = AppDimension.Space.sm
private val TooltipNotchWidth = 16.dp
private val TooltipNotchHeight = 8.dp
private val TooltipElevation = AppDimension.Elevation.small

// The card uses [AppUi.shapes.medium] which is a [androidx.compose.foundation.shape.RoundedCornerShape]
// of 10.dp; the notch must steer clear of that curve plus a small breathing margin so it
// does not visually flatten against the rounded edge.
private val TooltipCornerRadius = 10.dp
private val TooltipNotchSafeMargin = 4.dp

/**
 * Floating tooltip card overlaid on the chart canvas at [anchorPx]. Positioned above the
 * anchor by default, flipped below when the upper edge would be clipped, and clamped
 * horizontally so the card stays within the canvas bounds.
 *
 * The notch points at the anchor's X *when* the anchor falls inside the card's safe span
 * (everything outside the rounded-corner zone); when the card clamps to an edge and the
 * point sits over the corner curve, the notch is dropped entirely — the visual connection
 * is read from the card's proximity to the point, and a notch flattened against the
 * rounded corner reads worse than no notch at all.
 *
 * The popup must be rendered inside the canvas Box (sibling to the `Canvas`) and given the
 * same modifier as the Canvas so [anchorPx] resolves into the same coordinate space. It is
 * a chart-local overlay, NOT a window-level [androidx.compose.ui.window.Popup].
 */
@Composable
internal fun ChartTooltipPopup(
    tooltip: ChartTooltipUiModel,
    anchorPx: Offset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val notchWidthPx = with(density) { TooltipNotchWidth.toPx() }
    val notchHeightPx = with(density) { TooltipNotchHeight.toPx() }
    val spec = TooltipLayoutSpec(
        anchorGapPx = with(density) { TooltipAnchorGap.toPx() },
        edgeMarginPx = with(density) { TooltipEdgeMargin.toPx() },
        notchHeightPx = notchHeightPx,
        cornerRadiusPx = with(density) { TooltipCornerRadius.toPx() },
        notchSafeMarginPx = with(density) { TooltipNotchSafeMargin.toPx() },
    )

    SubcomposeLayout(modifier = modifier) { constraints ->
        val canvasW = constraints.maxWidth.toFloat()
        val maxCardWidth = (canvasW - spec.edgeMarginPx * 2f)
            .coerceAtLeast(0f)
            .toInt()
        val cardConstraints = Constraints(
            minWidth = 0,
            minHeight = 0,
            maxWidth = maxCardWidth,
            maxHeight = constraints.maxHeight,
        )

        val cardPlaceable = subcompose(TooltipSlot.Card) {
            TooltipCardBody(tooltip = tooltip, onClick = onClick)
        }.first().measure(cardConstraints)

        val layout = computeTooltipLayout(
            anchor = anchorPx,
            canvasSize = Size(canvasW, constraints.maxHeight.toFloat()),
            cardWidth = cardPlaceable.width.toFloat(),
            cardHeight = cardPlaceable.height.toFloat(),
            spec = spec,
        )

        val notchPlaceable = if (layout.showNotch) {
            subcompose(TooltipSlot.Notch) {
                TooltipNotch(pointDown = !layout.flipBelow)
            }.first().measure(
                Constraints.fixed(
                    width = notchWidthPx.toInt(),
                    height = notchHeightPx.toInt(),
                ),
            )
        } else {
            null
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            cardPlaceable.place(layout.cardLeft.toInt(), layout.cardTop.toInt())
            notchPlaceable?.place(
                x = (layout.cardLeft + layout.notchOffsetX - notchWidthPx / 2f).toInt(),
                y = if (layout.flipBelow) {
                    (layout.cardTop - notchHeightPx).toInt()
                } else {
                    (layout.cardTop + cardPlaceable.height).toInt()
                },
            )
        }
    }
}

private enum class TooltipSlot { Card, Notch }

@Composable
private fun TooltipCardBody(
    tooltip: ChartTooltipUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(
                elevation = TooltipElevation,
                shape = AppUi.shapes.medium,
            )
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier3)
            .clickable(onClick = onClick)
            .padding(AppDimension.cardPadding)
            .testTag("ChartTooltip"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = tooltip.exerciseName,
            style = AppUi.typography.titleSmall,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = tooltip.dateLabel,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textSecondary,
        )
        Text(
            text = tooltip.displayLabel,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.accent,
        )
        tooltip.setCountLabel?.let { label ->
            Text(
                text = label,
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun TooltipNotch(
    pointDown: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill = AppUi.colors.surfaceTier3
    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path().apply {
            if (pointDown) {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            } else {
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width / 2f, 0f)
            }
            close()
        }
        drawPath(path = path, color = fill)
    }
}

@Suppress("MagicNumber")
@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun ChartTooltipPopupAbovePreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.fillMaxSize().background(AppUi.colors.surfaceTier0)) {
            ChartTooltipPopup(
                tooltip = ChartTooltipUiModel(
                    sessionUuid = "session-1",
                    exerciseName = "Bench press",
                    dateLabel = "Apr 26, 2026",
                    displayLabel = "105 kg × 3",
                    setCountLabel = "2 sets this day",
                ),
                anchorPx = Offset(180f, 180f),
                onClick = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Suppress("MagicNumber")
@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun ChartTooltipPopupBelowFlippedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.fillMaxSize().background(AppUi.colors.surfaceTier0)) {
            ChartTooltipPopup(
                tooltip = ChartTooltipUiModel(
                    sessionUuid = "session-1",
                    exerciseName = "Pull-ups",
                    dateLabel = "26 апр. 2026",
                    displayLabel = "12 повторений",
                    setCountLabel = null,
                ),
                anchorPx = Offset(60f, 12f),
                onClick = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
