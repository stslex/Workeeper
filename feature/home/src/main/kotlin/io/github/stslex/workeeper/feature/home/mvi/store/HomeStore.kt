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
         * The start card's selected readout mode — **null until DataStore's first emission**,
         * and null renders no head label at all.
         *
         * Not seeded with WEEK. The mode is persisted (HS6), so a cold start does not know it
         * yet, and a seed is not a default: it is an announcement, in the most prominent
         * element of the screen, of a mode the user may never have chosen — on every launch,
         * not in some narrow window. It is also indistinguishable from a real reading of WEEK,
         * which is what makes it worse than an empty head.
         *
         * **HS3's default is not lost with the seed, because it never lived here.** It is
         * `CommonDataStoreImpl.DEFAULT_START_CARD_MODE` — the fallback the preference is READ
         * with — so a user who has never chosen still gets «Неделя», as the answer to "what is
         * persisted" rather than as a guess made while that question was still outstanding.
         *
         * It lands with [startCardBody] in one `copy` (see `CommonHandler.observeStartCard`),
         * so head and readout are never a frame apart.
         */
        val startCardMode: StartCardModeUi?,
        /**
         * The start card's readout, null until its flow's first emission. Not a second
         * loading discriminator: the card itself is gated by [showStartCta], and a null
         * body renders the shell without a reading for the frames before Room answers.
         */
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

        /**
         * Pending Active session conflict awaiting user choice. The Home picker tap routes
         * here when a different training already has an in-progress session; carrying this
         * in State (instead of as event-only data) keeps the modal stable across config
         * changes. `requestedTrainingUuid` lets Delete & start new resume the original
         * Start CTA flow after the active session is gone.
         */
        @Stable
        data class ConflictInfo(
            val activeSessionUuid: String,
            val requestedTrainingUuid: String,
            val activeSessionName: String,
            val progressLabel: String,
        )

        /**
         * Whether the ACTIVE-SESSION half of the screen has settled.
         *
         * **One-way, and the list's deferral depends on it.** `isActiveLoaded` goes false → true
         * once and never back, so the `if (isLoading)` branch in `HomeScreen` can only remove
         * `HomeBody` *before* a load has begun. Widening this to include the list — it read
         * `!isActiveLoaded || !isRecentLoaded` once — makes it two-way, and `rememberDeferredSurface`
         * lives inside `HomeBody`: the minimum hold would then be cancelled by the very state
         * change it exists to outlive.
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

            /**
             * The picker route, by itself: the card's `.setbar` «Другая тренировка» (§3.4),
             * which always opens the picker whatever the mode is. [OnStartActionClick] can
             * also end here — that is the handler's decision, not this action's meaning.
             */
            data object OnStartTrainingClick : Click

            /**
             * The card's primary button — ONE action for all four modes, carrying nothing.
             * Which branch it takes is §3.4's rule, decided in `ClickHandler` off the body
             * in state at click time; the card composable neither knows nor names the modes.
             */
            data object OnStartActionClick : Click

            data class OnPickerTrainingSelected(val trainingUuid: String) : Click

            // v2.3 — first row of the Start workout picker; routes to the blank-init Live
            // workout flow without a conflict check (the Start CTA is hidden when an
            // IN_PROGRESS session exists, so the user cannot reach this from a parallel
            // state).
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
