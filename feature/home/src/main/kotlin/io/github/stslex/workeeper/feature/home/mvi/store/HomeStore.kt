// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface HomeStore : Store<HomeStore.State, HomeStore.Action, HomeStore.Event> {

    @Stable
    data class State(
        val activeSession: ActiveSessionInfo?,
        val recent: ImmutableList<RecentSessionItem>,
        val nowMillis: Long,
        val isActiveLoaded: Boolean,
        val isRecentLoaded: Boolean,
        val picker: PickerState,
    ) : Store.State {

        @Stable
        data class ActiveSessionInfo(
            val sessionUuid: String,
            val trainingUuid: String,
            val trainingName: String,
            val startedAt: Long,
            val doneCount: Int,
            val totalCount: Int,
            val elapsedDurationLabel: String,
        ) {
            fun elapsedMillis(now: Long): Long = (now - startedAt).coerceAtLeast(0L)
        }

        @Stable
        sealed interface PickerState {
            @Stable
            data object Hidden : PickerState

            @Stable
            data class Visible(
                val templates: ImmutableList<PickerTrainingItem>,
                val isLoading: Boolean,
            ) : PickerState
        }

        val isLoading: Boolean get() = !isActiveLoaded || !isRecentLoaded
        val showStartCta: Boolean get() = activeSession == null && !isLoading
        val showRecentList: Boolean get() = recent.isNotEmpty()
        val showEmptyState: Boolean
            get() = !isLoading && activeSession == null && recent.isEmpty()
        val showPicker: Boolean get() = picker is PickerState.Visible

        companion object {
            val INITIAL = State(
                activeSession = null,
                recent = persistentListOf(),
                nowMillis = 0L,
                isActiveLoaded = false,
                isRecentLoaded = false,
                picker = PickerState.Hidden,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {
            data object OnActiveSessionClick : Click
            data object OnSettingsClick : Click
            data class OnRecentSessionClick(val sessionUuid: String) : Click
            data object OnStartTrainingClick : Click
            data class OnPickerTrainingSelected(val trainingUuid: String) : Click
            data object OnPickerSeeAllClick : Click
            data object OnPickerDismiss : Click
        }

        sealed interface Navigation : Action {
            data class OpenLiveWorkoutResume(val sessionUuid: String) : Navigation
            data class OpenLiveWorkoutFresh(val trainingUuid: String) : Navigation
            data class OpenPastSession(val sessionUuid: String) : Navigation
            data object OpenSettings : Navigation
            data object OpenAllTrainings : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common
            data object TimerTick : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
