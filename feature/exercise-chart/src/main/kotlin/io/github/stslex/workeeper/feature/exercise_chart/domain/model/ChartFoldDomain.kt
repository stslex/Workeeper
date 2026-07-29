// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

/**
 * The fold's whole answer: the day points and their footer. There is no render window —
 * the §4.6 canvas is index-spaced, so the visible span is the point list itself.
 */
data class ChartFoldDomain(
    val points: List<ChartPointDomain>,
    val footer: ChartFooterStatsDomain?,
)
