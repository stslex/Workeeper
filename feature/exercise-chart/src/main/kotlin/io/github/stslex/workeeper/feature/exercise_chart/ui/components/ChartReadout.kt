// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel

/**
 * The mockup's `.readout` (extraction §4.5) — the persistent inspection block that replaces
 * the tap tooltip: metric name and the active point's caption on the left, the value and its
 * unit on the right, the two columns bottom-aligned (`align-items:flex-end`).
 *
 * Type, derived (§0.2): the label is the kit `AppLabel` (the mockup's `.label` — uppercase
 * mono 11, `--dim`), prefixed with the 8dp molten `.mdot` (9px → 8 rung) when the active
 * point is the record. The caption is `.meta` — **mono** 12.5, `--meta`. The value is
 * `.data-hero` at its inline 38px → the 34 rung, `numeric.display` (Archivo `wdth 116` — the
 * hero's drawn `wdth 122` is a width the single bundled cut does not carry, B3's
 * reinstatement note covers it; its declared −.02em stays off the numeric slots per B4). The
 * unit is `.unit` at its inline 14px → 15 rung, mono, `--dim`.
 *
 * `min-height:78px` has no rung (extraction reports it; nearest are 72/80) — kept literal
 * at 80dp so a one-line caption and the record's three-part caption measure the same block.
 */
@Composable
internal fun ChartReadout(
    readout: ChartReadoutUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge)
            .heightIn(min = READOUT_MIN_HEIGHT)
            .testTag("ChartReadout"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (readout.isRecord) {
                    Box(
                        modifier = Modifier
                            .padding(end = AppDimension.Space.sm)
                            .size(RECORD_DOT_SIZE)
                            .clip(CircleShape)
                            .background(AppUi.colors.record.solid)
                            .testTag("ChartReadoutRecordDot"),
                    )
                }
                AppLabel(text = readout.metricName)
            }
            Spacer(modifier = Modifier.height(AppDimension.Space.sm))
            Text(
                text = readout.caption,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = readout.value,
                style = AppUi.typography.numeric.display,
                color = AppUi.colors.textPrimary,
                maxLines = 1,
            )
            Text(
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = AppDimension.Space.xs),
                text = readout.unit,
                style = AppUi.typography.mono.body,
                color = AppUi.colors.textDim,
                maxLines = 1,
            )
        }
    }
}

/** `.readout{min-height:78px}` — no rung (§4.5 reports it); literal, like the canvas's 212. */
private val READOUT_MIN_HEIGHT: Dp = 80.dp

/** `.mdot` — 9×9 molten-solid disc, 9px → the 8 rung; its 7px gap → `sm`. */
private val RECORD_DOT_SIZE: Dp = 8.dp

@Preview
@Composable
private fun ChartReadoutDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            ChartReadout(
                readout = ChartReadoutUiModel(
                    metricName = "Максимальный вес",
                    isRecord = false,
                    caption = "11 июля 2026 · 4 подхода",
                    value = "63",
                    unit = "кг",
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ChartReadoutRecordLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            ChartReadout(
                readout = ChartReadoutUiModel(
                    metricName = "Объём за сессию",
                    isRecord = true,
                    caption = "23 июля 2026 · 4 подхода · рекорд",
                    value = "4 620",
                    unit = "кг",
                ),
            )
        }
    }
}
