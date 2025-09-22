package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.State
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

internal interface ExercisesStore : Store<State, Action, Event> {

    data class State(
        val items: PagingUiState<PagingData<ExerciseUiModel>>,
        val selectedItems: ImmutableSet<ExerciseUiModel>,
        val query: String,
    ) : Store.State {

        companion object {

            internal fun init(
                allItems: PagingUiState<PagingData<ExerciseUiModel>>
            ): State = State(
                items = allItems,
                selectedItems = persistentSetOf(),
                query = "",
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            @Suppress("Unused")
            data object Init : Paging
        }

        sealed interface Input : Action {

            data class SearchQuery(val query: String) : Input
        }

        sealed interface Click : Action {

            data object FloatButtonClick : Click

            data class Item(val item: ExerciseUiModel) : Click

            data class LonkClick(val item: ExerciseUiModel) : Click
        }

        sealed interface Navigation : Action {

            data object CreateExerciseDialog : Navigation

            data class OpenExercise(val uuid: String) : Navigation
        }

    }

    sealed interface Event : Store.Event {

        data class HapticFeedback(
            val type: HapticFeedbackType
        ) : Event
    }

}
