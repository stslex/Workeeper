// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

import java.time.LocalDate

internal data class ChartFoldDomain(
    val points: List<ChartPointDomain>,
    val footer: ChartFooterStatsDomain?,
    val windowStartDay: LocalDate?,
    val windowEndDay: LocalDate?,
)
