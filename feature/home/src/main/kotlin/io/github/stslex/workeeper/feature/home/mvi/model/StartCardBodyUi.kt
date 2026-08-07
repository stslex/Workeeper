// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

/**
 * The start card's readout — the body inside the one shell (home-start-card.md HS1: the mode
 * changes the body only; the head, the action and the geometry stay).
 *
 * One variant per shipped mode; the other three land with commit 2 of this arc.
 */
@Stable
sealed interface StartCardBodyUi {

    /**
     * «Неделя» (§3.1): sessions finished this calendar week plus one pill per weekday.
     * All labels arrive pre-formatted — the count as digits for the Archivo slot, the unit
     * plural already resolved.
     */
    @Immutable
    data class Week(
        val sessionsCountLabel: String,
        val sessionsUnitLabel: String,
        val days: ImmutableList<WeekDayUi>,
    ) : StartCardBodyUi
}

/** One weekday cell of the «Неделя» rail: its label and whether a session finished on it. */
@Immutable
data class WeekDayUi(
    val label: String,
    val isFilled: Boolean,
)
