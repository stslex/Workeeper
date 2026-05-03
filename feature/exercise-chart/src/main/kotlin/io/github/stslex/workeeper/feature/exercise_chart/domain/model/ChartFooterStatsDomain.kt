// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

internal data class ChartFooterStatsDomain(
    val min: ChartPointDomain,
    val max: ChartPointDomain,
    val last: ChartPointDomain,
)
