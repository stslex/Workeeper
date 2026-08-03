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
 * The mockup's `.tabs` (extraction §4.3) — the metric strip with a **sliding indicator**, not
 * a per-button lift: unlike `.mseg` (whose treatment is `AppSegmentedControl`), the mockup
 * gives `.tabs` a dedicated `.ind` element that travels between buttons, so this ships as the
 * chart's own component rather than a restyle of the shared one.
 *
 * Geometry, derived (§0.2): track `--sec`, radius 14px → 16dp, padding 5px → 4dp; buttons
 * flex-1, height 44px → 48dp, 14px/500 → `text.body` at Medium, `--meta` → `--max` when on,
 * button radius 10px → 8dp, gap 4dp. The indicator is a **lifted surface** — `--slab` +
 * `--slabtop`, one of `liftedSurface`'s four commissioned consumers (its KDoc cites pass2d L137).
 *
 * **The thumb's radius is not the button's.** It is `TRACK_RADIUS - TRACK_PADDING` = 12dp, so its
 * corners nest concentrically inside the track's 16dp; the drawn 10px belongs to the buttons.
 * Shipping the button's 8dp on the thumb — which is what this did until the shape was corrected —
 * leaves the thumb's corners tighter than the track's and the track visible behind them.
 *
 * The mockup animates `left`/`width` over 320ms — a duration the motion scale does not have
 * (140/260/520, and "anything else is a design decision"). The travel runs on `base` (260ms) on
 * the [AppMotion.travel] curve, **not** the standard `out`: §26.2 measured `out`'s near-expo tail
 * leaving this thumb creeping across 58.1% of the animation, now 37.5%. Read the curve off
 * [metricIndicatorSpec], never off this paragraph — `MetricTabsMotionTest` asserts it is `travel`
 * and asserts it is not `out`. The −60ms is reported with the PR rather than minted as a seventh
 * duration. Width needs no animation here: the buttons are equal-flex, so every stop measures the
 * same.
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
        // DERIVED, and it must stay derived. The thumb is inset by TRACK_PADDING inside a track
        // rounded at TRACK_RADIUS, so its corners nest concentrically only at
        // `TRACK_RADIUS - TRACK_PADDING`. Do NOT put [tabShape] here: it is the obvious-looking
        // choice, it is what this used to be, and at 8dp against a 16dp track inset by 4dp the
        // thumb's corners stop following the track's — the two curves diverge and the track shows
        // through at each corner. Writing the answer as a literal 12dp is the same bug deferred:
        // it is correct today and silently wrong the moment either token moves.
        val thumbShape = RoundedCornerShape(TRACK_RADIUS - TRACK_PADDING)
        val indicatorOffset by animateDpAsState(
            targetValue = (tabWidth + TRACK_GAP) * selectedIndex,
            animationSpec = metricIndicatorSpec(AppUi.motion),
            label = "tabs-ind",
        )

        // GEL, restored — §26's `.tabs` row is under test here, not being overruled by taste.
        // That row withheld the stretch because this indicator "animates `left` AND `width`", and
        // that premise measured FALSE on device: the thumb is 318px in every frame of a two-step
        // jump, because `tabWidth` divides the track into three equal stops. So the restriction
        // the row derives — gel is for indicators that move at constant size — does not exclude
        // this one, it includes it.
        //
        // Offset and scale on DIFFERENT elements, as on the pill: one `transform` cannot carry
        // both, and the drawing says the same (`.ind` transforms, `.ind i` animates).
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
                // NO RIPPLE — see `rememberPressScale`. `clickable`'s default indication is one,
                // nothing in the drawing has a ripple, and this surface took the default rather
                // than deciding. The thumb is `liftedSurface` and sits UNDER the label, so the
                // scale goes on the label box: scaling the tab would scale a transparent box.
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

/** `.tabs{padding:5px}` → 4dp. */
/**
 * The indicator's transit — extracted so the CURVE is assertable and not only the duration.
 *
 * §26.2: this was `out`, whose near-expo tail put 90% of the travel in the first third and left the
 * thumb creeping (device-measured tail 58.1% of the animation, now 37.5%). The extraction is what
 * makes a repoint back to `out` reddenable; inline, it was invisible to every test in the module.
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
