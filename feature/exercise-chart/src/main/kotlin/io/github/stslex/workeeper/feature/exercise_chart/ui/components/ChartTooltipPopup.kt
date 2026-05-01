// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
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
    val notchWidthPx = with(density) { TooltipNotchWidth.roundToPx().toFloat() }
    val notchHeightPx = with(density) { TooltipNotchHeight.roundToPx().toFloat() }
    val tooltipFill = AppUi.colors.surfaceTier3
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
            TooltipContent(tooltip = tooltip)
        }.first().measure(cardConstraints)

        val layout = computeTooltipLayout(
            anchor = anchorPx,
            canvasSize = Size(canvasW, constraints.maxHeight.toFloat()),
            cardWidth = cardPlaceable.width.toFloat(),
            cardHeight = cardPlaceable.height.toFloat(),
            spec = spec,
        )

        val tooltipHeight = cardPlaceable.height + if (layout.showNotch) {
            notchHeightPx.toInt()
        } else {
            0
        }
        val tooltipConstraints = Constraints.fixed(
            width = cardPlaceable.width,
            height = tooltipHeight,
        )
        val tooltipPlaceable = subcompose(TooltipSlot.Tooltip) {
            TooltipShape(
                spec = TooltipShapeSpec(
                    showNotch = layout.showNotch,
                    notchPointsDown = !layout.flipBelow,
                    notchOffsetX = layout.notchOffsetX,
                    cornerRadiusPx = spec.cornerRadiusPx,
                    notchHalfWidthPx = notchWidthPx / 2f,
                    notchHeightPx = notchHeightPx,
                    fillColor = tooltipFill,
                ),
                onClick = onClick,
            ) {
                TooltipContent(tooltip = tooltip)
            }
        }.first().measure(tooltipConstraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            val tooltipTop = if (layout.showNotch && layout.flipBelow) {
                layout.cardTop - notchHeightPx
            } else {
                layout.cardTop
            }
            tooltipPlaceable.place(layout.cardLeft.toInt(), tooltipTop.toInt())
        }
    }
}

private enum class TooltipSlot { Card, Tooltip }

@Composable
private fun TooltipShape(
    spec: TooltipShapeSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = remember(spec) { spec.toShape() }
    val notchHeight = if (spec.showNotch) {
        spec.notchHeightPx.toInt()
    } else {
        0
    }

    Layout(
        content = content,
        modifier = modifier
            .shadow(
                elevation = TooltipElevation,
                shape = shape,
                clip = false,
            )
            .background(color = spec.fillColor, shape = shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("ChartTooltip"),
    ) { measurables, constraints ->
        val contentMaxHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - notchHeight).coerceAtLeast(0)
        } else {
            constraints.maxHeight
        }
        val contentConstraints = constraints.copy(
            minHeight = 0,
            maxHeight = contentMaxHeight,
        )
        val placeable = measurables.first().measure(contentConstraints)
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height + notchHeight)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val contentY = if (spec.showNotch && !spec.notchPointsDown) {
            notchHeight
        } else {
            0
        }

        layout(width, height) {
            placeable.place(0, contentY)
        }
    }
}

@Composable
private fun TooltipContent(
    tooltip: ChartTooltipUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(AppDimension.cardPadding),
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

@Immutable
private data class TooltipShapeSpec(
    val showNotch: Boolean,
    val notchPointsDown: Boolean,
    val notchOffsetX: Float,
    val cornerRadiusPx: Float,
    val notchHalfWidthPx: Float,
    val notchHeightPx: Float,
    val fillColor: Color,
)

private fun TooltipShapeSpec.toShape(): Shape = GenericShape { size, _ ->
    addTooltipOutline(size = size, spec = this@toShape)
}

private fun Path.addTooltipOutline(
    size: Size,
    spec: TooltipShapeSpec,
) {
    if (!spec.showNotch) {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(spec.cornerRadiusPx),
            ),
        )
        return
    }

    val cardTop = if (spec.notchPointsDown) 0f else spec.notchHeightPx
    val cardBottom = if (spec.notchPointsDown) {
        size.height - spec.notchHeightPx
    } else {
        size.height
    }
    val cornerRadius = spec.cornerRadiusPx.coerceAtMost(size.width / 2f)
        .coerceAtMost((cardBottom - cardTop) / 2f)

    if (spec.notchPointsDown) {
        addBottomNotchOutline(
            width = size.width,
            cardTop = cardTop,
            cardBottom = cardBottom,
            cornerRadius = cornerRadius,
            notchOffsetX = spec.notchOffsetX,
            notchHalfWidth = spec.notchHalfWidthPx,
            notchTipY = size.height,
        )
    } else {
        addTopNotchOutline(
            width = size.width,
            cardTop = cardTop,
            cardBottom = cardBottom,
            cornerRadius = cornerRadius,
            notchOffsetX = spec.notchOffsetX,
            notchHalfWidth = spec.notchHalfWidthPx,
            notchTipY = 0f,
        )
    }
}

private fun Path.addBottomNotchOutline(
    width: Float,
    cardTop: Float,
    cardBottom: Float,
    cornerRadius: Float,
    notchOffsetX: Float,
    notchHalfWidth: Float,
    notchTipY: Float,
) {
    moveTo(cornerRadius, cardTop)
    lineTo(width - cornerRadius, cardTop)
    arcTo(
        rect = Rect(
            left = width - 2 * cornerRadius,
            top = cardTop,
            right = width,
            bottom = cardTop + 2 * cornerRadius,
        ),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(width, cardBottom - cornerRadius)
    arcTo(
        rect = Rect(
            left = width - 2 * cornerRadius,
            top = cardBottom - 2 * cornerRadius,
            right = width,
            bottom = cardBottom,
        ),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(notchOffsetX + notchHalfWidth, cardBottom)
    lineTo(notchOffsetX, notchTipY)
    lineTo(notchOffsetX - notchHalfWidth, cardBottom)
    lineTo(cornerRadius, cardBottom)
    arcTo(
        rect = Rect(
            left = 0f,
            top = cardBottom - 2 * cornerRadius,
            right = 2 * cornerRadius,
            bottom = cardBottom,
        ),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(0f, cardTop + cornerRadius)
    arcTo(
        rect = Rect(
            left = 0f,
            top = cardTop,
            right = 2 * cornerRadius,
            bottom = cardTop + 2 * cornerRadius,
        ),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    close()
}

private fun Path.addTopNotchOutline(
    width: Float,
    cardTop: Float,
    cardBottom: Float,
    cornerRadius: Float,
    notchOffsetX: Float,
    notchHalfWidth: Float,
    notchTipY: Float,
) {
    moveTo(cornerRadius, cardTop)
    lineTo(notchOffsetX - notchHalfWidth, cardTop)
    lineTo(notchOffsetX, notchTipY)
    lineTo(notchOffsetX + notchHalfWidth, cardTop)
    lineTo(width - cornerRadius, cardTop)
    arcTo(
        rect = Rect(
            left = width - 2 * cornerRadius,
            top = cardTop,
            right = width,
            bottom = cardTop + 2 * cornerRadius,
        ),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(width, cardBottom - cornerRadius)
    arcTo(
        rect = Rect(
            left = width - 2 * cornerRadius,
            top = cardBottom - 2 * cornerRadius,
            right = width,
            bottom = cardBottom,
        ),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(cornerRadius, cardBottom)
    arcTo(
        rect = Rect(
            left = 0f,
            top = cardBottom - 2 * cornerRadius,
            right = 2 * cornerRadius,
            bottom = cardBottom,
        ),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(0f, cardTop + cornerRadius)
    arcTo(
        rect = Rect(
            left = 0f,
            top = cardTop,
            right = 2 * cornerRadius,
            bottom = cardTop + 2 * cornerRadius,
        ),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    close()
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
