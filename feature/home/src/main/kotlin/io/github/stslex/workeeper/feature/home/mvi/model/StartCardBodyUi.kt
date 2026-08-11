// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

/**
 * The start card's readout — the body inside the one shell (home-start-card.md HS1: the mode
 * changes the body only; the head, the action and the geometry stay).
 *
 * One variant per mode's populated state plus [Empty], which carries the selected mode's own
 * copy (HD2–HD4): a mode with nothing to show says so and stays selected — it never renders
 * a sibling mode's readout.
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

    /** «Дни без тренировки» (§3.2): the gap as digits + unit, anchored by name and date. */
    @Immutable
    data class DaysSince(
        val daysCountLabel: String,
        val daysUnitLabel: String,
        val anchorLabel: String,
    ) : StartCardBodyUi

    /**
     * «Отставшие группы» (§3.3): up to three tags, longest idle first. The word «дней»
     * appears once, in [footnoteLabel] — the rows carry bare numbers.
     */
    @Immutable
    data class TagIdle(
        val rows: ImmutableList<TagIdleRowUi>,
        val footnoteLabel: String,
    ) : StartCardBodyUi

    /**
     * «Забытая тренировка» (§3.4): the template's name over one meta line (days idle — or
     * «ещё ни разу» for a never-run template (HD1) — and its composition). The card's
     * primary action starts THIS training; [trainingUuid] is what the click carries.
     */
    @Immutable
    data class Forgotten(
        val trainingUuid: String,
        val trainingName: String,
        val metaLabel: String,
    ) : StartCardBodyUi

    /** The selected mode's own empty state — copy per mode, mode stays selected (HD2–HD4). */
    @Immutable
    data class Empty(
        val message: String,
    ) : StartCardBodyUi
}

/** One weekday cell of the «Неделя» rail: its label and whether a session finished on it. */
@Immutable
data class WeekDayUi(
    val label: String,
    val isFilled: Boolean,
)

/** One «Отставшие группы» row: name, bar proportional to idleness, bare day count. */
@Immutable
data class TagIdleRowUi(
    val name: String,
    val barFraction: Float,
    val daysCountLabel: String,
)
