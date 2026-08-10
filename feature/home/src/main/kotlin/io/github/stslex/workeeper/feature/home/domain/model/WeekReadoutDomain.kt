// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

/**
 * The «Неделя» readout (home-start-card.md §3.1): how many sessions finished in the current
 * calendar week, and on which weekdays.
 *
 * [trainedDayIndexes] is Monday-first: 0 = Monday … 6 = Sunday.
 */
data class WeekReadoutDomain(
    val sessionsThisWeek: Int,
    val trainedDayIndexes: Set<Int>,
)
