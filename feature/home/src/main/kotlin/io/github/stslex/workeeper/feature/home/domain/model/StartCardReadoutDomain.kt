// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

/**
 * What the start card has to show for the selected mode (home-start-card.md §3) — data or that
 * mode's own empty state, never a sibling's readout. [NoSessions] serves the first two modes.
 */
sealed interface StartCardReadoutDomain {

    /** «Неделя» with at least one session ever logged. */
    data class Week(val readout: WeekReadoutDomain) : StartCardReadoutDomain

    /** «Дни без тренировки»: the gap and the session that anchors it. */
    data class DaysSince(
        val daysSince: Int,
        val lastTrainingName: String,
        val lastIsAdhoc: Boolean,
        val lastFinishedAt: Long,
    ) : StartCardReadoutDomain

    /** «Неделя» / «Дни без тренировки» on a history with zero finished sessions. */
    data object NoSessions : StartCardReadoutDomain

    /** «Отставшие группы»: up to three tags, longest idle first. */
    data class TagIdle(val entries: List<Entry>) : StartCardReadoutDomain {
        data class Entry(val name: String, val daysIdle: Int)
    }

    /** «Отставшие группы» with no tagged training history to measure. */
    data object NoTaggedHistory : StartCardReadoutDomain

    /** «Забытая тренировка»: the most forgotten template; [daysIdle] is null if never run. */
    data class Forgotten(
        val trainingUuid: String,
        val trainingName: String,
        val daysIdle: Int?,
        val exerciseCount: Int,
    ) : StartCardReadoutDomain

    /** «Забытая тренировка» with no templates at all. */
    data object NoTemplates : StartCardReadoutDomain
}
