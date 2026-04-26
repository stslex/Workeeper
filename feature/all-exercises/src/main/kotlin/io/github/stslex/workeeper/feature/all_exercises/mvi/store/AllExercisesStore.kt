// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal interface AllExercisesStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val pagingUiState: PagingUiState<PagingData<ExerciseUiModel>>,
        val availableTags: ImmutableList<TagUiModel>,
        val activeTagFilter: ImmutableSet<String>,
    ) : Store.State {

        companion object {

            fun init(
                pagingUiState: PagingUiState<PagingData<ExerciseUiModel>>,
            ): State = State(
                pagingUiState = pagingUiState,
                availableTags = persistentListOf(),
                activeTagFilter = persistentSetOf(),
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Click : Action {

            data class OnExerciseClick(val uuid: String) : Click

            data object OnFabClick : Click

            data class OnTagFilterToggle(val tagUuid: String) : Click

            data class OnArchiveSwipe(val uuid: String, val name: String) : Click

            data class OnUndoArchive(val uuid: String) : Click
        }

        sealed interface Navigation : Action {

            data class OpenDetail(val uuid: String) : Navigation

            data object OpenCreate : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val name: String, val uuid: String) : Event

        data class ShowArchiveBlocked(val trainings: List<String>) : Event
    }
}
