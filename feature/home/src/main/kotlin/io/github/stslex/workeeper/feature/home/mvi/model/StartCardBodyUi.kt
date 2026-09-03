// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

/**
 * The start card's readout — the body inside the one shell (home-start-card.md HS1). [Empty]
 * carries the selected mode's own copy; a mode never renders a sibling's readout.
 */
@Stable
sealed interface StartCardBodyUi {

    /** «Неделя» (§3.1): sessions finished this calendar week plus one pill per weekday. */
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

    /** «Отставшие группы» (§3.3): up to three tags, longest idle first; bare row numbers. */
    @Immutable
    data class TagIdle(
        val rows: ImmutableList<TagIdleRowUi>,
        val footnoteLabel: String,
    ) : StartCardBodyUi

    /** «Забытая тренировка» (§3.4): the template the primary action starts, and its meta. */
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
