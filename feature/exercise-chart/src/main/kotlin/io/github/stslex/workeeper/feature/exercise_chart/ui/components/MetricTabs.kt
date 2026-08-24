// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import io.github.stslex.workeeper.core.ui.kit.components.rememberIndicatorGel
import io.github.stslex.workeeper.core.ui.kit.components.rememberPressScale
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppMotion
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel

/**
 * The mockup's `.tabs` (extraction §4.3): the metric strip with a sliding indicator, shipped
 * as the chart's own component rather than a restyle of `AppSegmentedControl`.
 */
@Composable
internal fun MetricTabs(
    selected: ChartMetricUiModel,
    onSelect: (ChartMetricUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = ChartMetricUiModel.entries
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TRACK_RADIUS))
            .background(AppUi.colors.surfaceTier1)
            .padding(TRACK_PADDING)
            .testTag("ChartMetricTabs"),
    ) {
        val tabWidth = (maxWidth - TRACK_GAP * (entries.size - 1)) / entries.size
        val selectedIndex = entries.indexOf(selected)
        // The TAB boxes' own clip — the press target, not the indicator.
        val tabShape = RoundedCornerShape(AppDimension.Radius.small)
        // GUARD: the thumb's radius must stay derived — it nests concentrically inside the
        // track only at `TRACK_RADIUS - TRACK_PADDING`. Not [tabShape], not a 12dp literal.
        val thumbShape = RoundedCornerShape(TRACK_RADIUS - TRACK_PADDING)
        val indicatorOffset by animateDpAsState(
            targetValue = (tabWidth + TRACK_GAP) * selectedIndex,
            animationSpec = metricIndicatorSpec(AppUi.motion),
            label = "tabs-ind",
        )

        // Gel applies here: the thumb is constant-width (equal-flex stops), which is exactly
        // what §26 restricts the stretch to. Offset and scale sit on different elements.
        val gel = rememberIndicatorGel(
            selectedIndex = selectedIndex,
            itemPitch = tabWidth + TRACK_GAP,
            trackWidth = maxWidth + TRACK_PADDING * 2,
            travelMillis = AppUi.motion.base,
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(tabWidth)
                .height(TAB_HEIGHT),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = gel.scale.value
                        transformOrigin = gel.origin
                    }
                    .liftedSurface(shape = thumbShape),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(TRACK_GAP)) {
            entries.forEach { metric ->
                val labelColor by animateColorAsState(
                    targetValue = if (metric == selected) {
                        AppUi.colors.textPrimary
                    } else {
                        AppUi.colors.textTertiary
                    },
                    animationSpec = tween(
                        durationMillis = AppUi.motion.base,
                        easing = AppUi.motion.out,
                    ),
                    label = "tab-label",
                )
                // NO RIPPLE — see `rememberPressScale`. The scale goes on the label box; the
                // thumb is a `liftedSurface` under it, so scaling the tab would scale nothing.
                val interactionSource = remember { MutableInteractionSource() }
                val pressScale by rememberPressScale(interactionSource)
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(TAB_HEIGHT)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(tabShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onSelect(metric) }
                        .testTag("ChartMetricTab_${metric.name}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(metric.labelRes),
                        style = AppUi.typography.text.body.copy(fontWeight = FontWeight.Medium),
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The indicator's transit, extracted so the CURVE is assertable and not only the duration —
 * `MetricTabsMotionTest` pins `travel` and rejects `out`.
 */
internal fun <T> metricIndicatorSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = motion.travel,
)

/** The track's own corner. [thumbShape] is derived from it — see the guard at the site. */
private val TRACK_RADIUS = AppDimension.Radius.medium

private val TRACK_PADDING = AppDimension.Space.xs

/** `.tabtrack{gap:4px}` → 4dp. */
private val TRACK_GAP = AppDimension.Space.xs

/** `.tabs button{height:44px}` → the 48 rung. */
private val TAB_HEIGHT = AppDimension.heightMd

@Preview
@Composable
private fun MetricTabsLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            MetricTabs(selected = ChartMetricUiModel.HEAVIEST_WEIGHT, onSelect = {})
        }
    }
}

@Preview
@Composable
private fun MetricTabsDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            MetricTabs(selected = ChartMetricUiModel.VOLUME_PER_SESSION, onSelect = {})
        }
    }
}
