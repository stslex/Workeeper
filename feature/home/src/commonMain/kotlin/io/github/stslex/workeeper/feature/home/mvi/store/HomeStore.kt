// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi

interface HomeStore : Store<HomeStore.State, HomeStore.Action, HomeStore.Event> {

    @Stable
    data class State(
        val activeSession: ActiveSessionInfo?,

        /**
         * The start card's readout mode — null until DataStore's first emission, and a null
         * renders no head label. HS3's «Неделя» default is the DataStore read's fallback.
         */
        val startCardMode: StartCardModeUi?,
        /** The start card's readout, null until its flow's first emission. */
        val startCardBody: StartCardBodyUi?,
        val pagingUiState: PagingUiState<PagingData<RecentSessionItem>>,
        val nowMillis: Long,
        val isActiveLoaded: Boolean,
        val bottomSheet: BottomSheetState,
        val pendingConflict: ConflictInfo?,
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

        /** A pending active-session conflict, in State so the modal survives config changes. */
        @Stable
        data class ConflictInfo(
            val activeSessionUuid: String,
            val requestedTrainingUuid: String,
            val activeSessionName: String,
            val progressLabel: String,
        )

        /**
         * Whether the ACTIVE-SESSION half has settled. GUARD: keep it one-way — a term for the
         * list makes it two-way and cancels the deferred surface's hold inside `HomeBody`.
         */
        val isLoading: Boolean get() = !isActiveLoaded
        val showStartCta: Boolean get() = activeSession == null && !isLoading

        companion object {

            fun init(
                pagingUiState: PagingUiState<PagingData<RecentSessionItem>>,
            ): State = State(
                activeSession = null,
                // Both null, together: nothing is known about the card until DataStore says.
                startCardMode = null,
                startCardBody = null,
                pagingUiState = pagingUiState,
                nowMillis = 0L,
                isActiveLoaded = false,
                bottomSheet = BottomSheetState.Hidden,
                pendingConflict = null,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {

            /** The card's head (HS4): the mode label + caret, opening the mode sheet. */
            data object OnModeLabelClick : Click

            /** A row of the mode sheet: persist the mode (HS6) and close the sheet. */
            data class OnModeSelected(val mode: StartCardModeUi) : Click

            data object OnModeSheetDismiss : Click

            data object OnActiveSessionClick : Click
            data object OnChartsClick : Click
            data object OnSettingsClick : Click
            data class OnRecentSessionClick(val sessionUuid: String) : Click

            /** The card's `.setbar` «Другая тренировка» (§3.4): always opens the picker. */
            data object OnStartTrainingClick : Click

            /** The card's primary button; `ClickHandler` picks the branch off the body. */
            data object OnStartActionClick : Click

            data class OnPickerTrainingSelected(val trainingUuid: String) : Click

            // First row of the Start workout picker; no conflict check because the Start CTA
            // is hidden while an IN_PROGRESS session exists.
            data object OnStartBlankClick : Click

            data object OnPickerSeeAllClick : Click
            data object OnPickerDismiss : Click
            data object OnConflictResume : Click
            data object OnConflictDeleteAndStart : Click
            data object OnConflictDismiss : Click
        }

        sealed interface Navigation : Action {
            data class OpenLiveWorkoutResume(val sessionUuid: String) : Navigation
            data class OpenLiveWorkoutFresh(val trainingUuid: String) : Navigation
            data object OpenLiveWorkoutBlank : Navigation
            data class OpenPastSession(val sessionUuid: String) : Navigation
            data object OpenSettings : Navigation
            data object OpenCharts : Navigation
            data object OpenAllTrainings : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
