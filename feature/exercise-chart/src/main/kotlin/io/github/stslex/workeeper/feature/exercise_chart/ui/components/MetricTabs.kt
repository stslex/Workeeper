// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel

/**
 * The mockup's `.tabs` (extraction §4.3) — the metric strip with a **sliding indicator**, not
 * a per-button lift: unlike `.mseg` (whose treatment is `AppSegmentedControl`), the mockup
 * gives `.tabs` a dedicated `.ind` element that travels between buttons, so this ships as the
 * chart's own component rather than a restyle of the shared one.
 *
 * Geometry, derived (§0.2): track `--sec`, radius 14px → 16dp, padding 5px → 4dp; buttons
 * flex-1, height 44px → 48dp, 14px/500 → `text.body` at Medium, `--meta` → `--max` when on,
 * radius 10px → 8dp, gap 4dp. The indicator is a **lifted surface** — `--slab` + `--slabtop`,
 * one of `liftedSurface`'s four commissioned consumers (its KDoc cites pass2d L137).
 *
 * The mockup animates `left`/`width` over 320ms — a duration the motion scale does not have
 * (140/260/520, and "anything else is a design decision"). The travel runs on `base` (260ms)
 * with the standard `out` curve; the −60ms is reported with the PR rather than minted as a
 * seventh duration. Width needs no animation here: the buttons are equal-flex, so every stop
 * measures the same.
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
            .clip(RoundedCornerShape(AppDimension.Radius.medium))
            .background(AppUi.colors.surfaceTier1)
            .padding(TRACK_PADDING)
            .testTag("ChartMetricTabs"),
    ) {
        val tabWidth = (maxWidth - TRACK_GAP * (entries.size - 1)) / entries.size
        val selectedIndex = entries.indexOf(selected)
        val thumbShape = RoundedCornerShape(AppDimension.Radius.small)
        val indicatorOffset by animateDpAsState(
            targetValue = (tabWidth + TRACK_GAP) * selectedIndex,
            animationSpec = tween(
                durationMillis = AppUi.motion.base,
                easing = AppUi.motion.out,
            ),
            label = "tabs-ind",
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(tabWidth)
                .height(TAB_HEIGHT)
                .liftedSurface(shape = thumbShape),
        )

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
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(TAB_HEIGHT)
                        .clip(thumbShape)
                        .clickable { onSelect(metric) }
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

/** `.tabs{padding:5px}` → 4dp. */
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
