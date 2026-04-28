// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel

internal interface PastSessionStore :
    Store<PastSessionStore.State, PastSessionStore.Action, PastSessionStore.Event> {

    @Stable
    data class State(
        val sessionUuid: String,
        val phase: Phase,
        val deleteDialogVisible: Boolean,
    ) : Store.State {

        @Stable
        sealed interface Phase {
            @Stable
            data object Loading : Phase

            @Stable
            data class Loaded(val detail: PastSessionUiModel) : Phase

            @Stable
            data class Error(val errorType: ErrorType) : Phase
        }

        val canDelete: Boolean get() = phase is Phase.Loaded

        companion object {

            fun create(sessionUuid: String): State = State(
                sessionUuid = sessionUuid,
                phase = Phase.Loading,
                deleteDialogVisible = false,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {
            data object OnBackClick : Click
            data object OnDeleteClick : Click
            data object OnDeleteConfirm : Click
            data object OnDeleteDismiss : Click
            data class OnSetTypeChange(
                val setUuid: String,
                val type: SetTypeUiModel,
            ) : Click

            data object OnRetryLoad : Click
        }

        sealed interface Input : Action {
            data class OnSetWeightChange(val setUuid: String, val raw: String) : Input
            data class OnSetRepsChange(val setUuid: String, val raw: String) : Input
        }

        sealed interface Navigation : Action {
            data object Back : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
        data class ShowError(val errorType: ErrorType) : Event
        data object DeletedSnackbar : Event
        data object SaveFailedSnackbar : Event
    }
}
