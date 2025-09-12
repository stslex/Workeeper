package io.github.stslex.workeeper.feature.home.ui.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

interface HomeStore : Store<State, Action, Event> {

    data class State(
        val allState: HomeAllState,
        val chartsState: HomeChartsState
    ) : Store.State {

        internal fun copyAll(
            selectedItems: ImmutableSet<ExerciseUiModel> = allState.selectedItems,
            query: String = allState.query,
        ): State = copy(
            allState = allState.copy(
                selectedItems = selectedItems,
                query = query
            ),
        )

        internal fun copyCharts(
            name: String = chartsState.name,
            charts: ImmutableList<SingleChartUiModel> = chartsState.charts,
            startDate: DateProperty = chartsState.startDate,
            endDate: DateProperty = chartsState.endDate,
            calendarState: CalendarState = chartsState.calendarState
        ): State = copy(
            chartsState = chartsState.copy(
                name = name,
                charts = charts,
                startDate = startDate,
                endDate = endDate,
                calendarState = calendarState
            )
        )

        companion object {

            internal fun init(
                allItems: PagingUiState<PagingData<ExerciseUiModel>>
            ): State = State(
                allState = HomeAllState.init(allItems),
                chartsState = HomeChartsState.INITIAL
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Input : Action {

            data class SearchQuery(val query: String) : Input

            data class ChangeStartDate(val timestamp: Long) : Input

            data class ChangeEndDate(val timestamp: Long) : Input
        }

        sealed interface Click : Action {

            data object FloatButtonClick : Click

            data class Item(val item: ExerciseUiModel) : Click

            data class LonkClick(val item: ExerciseUiModel) : Click

            sealed interface Calendar : Click {

                data object StartDate : Calendar

                data object EndDate : Calendar

                data object Close : Calendar
            }
        }

        sealed interface Navigation : Action {

            data object CreateExerciseDialog : Navigation

            data class OpenExercise(val data: Data) : Navigation
        }

    }

    sealed interface Event : Store.Event {

        data class HapticFeedback(
            val type: HapticFeedbackType
        ) : Event
    }

}
