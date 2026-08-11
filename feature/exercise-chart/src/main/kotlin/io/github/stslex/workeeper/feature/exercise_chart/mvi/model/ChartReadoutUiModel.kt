// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

/**
 * The mockup's `.readout` (extraction §4.5) — the persistent inspection block above the
 * canvas, fed by the active (scrubbed) point. Everything is pre-formatted by the UI mapper;
 * [metricName] is the metric's long name (`Максимальный вес`, not the tab's `Вес`) and is
 * uppercased by `AppLabel` at render, not here — casing is a property of the style.
 */
data class ChartReadoutUiModel(
    val metricName: String,
    val isRecord: Boolean,
    val caption: String,
    val value: String,
    val unit: String,
)
