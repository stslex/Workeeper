package io.github.stslex.workeeper.feature.charts.ui.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.SingleChartUiModel
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface ChartsStore : Store<State, Action, Event> {

    data class State(
        val name: String,
        val charts: ImmutableList<SingleChartUiModel>,
        val startDate: PropertyHolder.DateProperty,
        val endDate: PropertyHolder.DateProperty,
        val type: ChartsType,
        val calendarState: CalendarState,
    ) : Store.State {

        companion object {

            val INITIAL = State(
                name = "",
                charts = persistentListOf(),
                startDate = PropertyHolder.DateProperty(
                    initialValue = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000),
                ), // 7 days default
                endDate = PropertyHolder.DateProperty(System.currentTimeMillis()),
                type = ChartsType.TRAINING,
                calendarState = CalendarState.Closed,
            )
        }
    }

    sealed interface Action : Store.Action {

        sealed interface Paging : Action {

            data object Init : Paging
        }

        sealed interface Input : Action {

            data class ChangeStartDate(val timestamp: Long) : Input

            data class ChangeEndDate(val timestamp: Long) : Input
        }

        sealed interface Click : Action {

            data class ChangeType(val type: ChartsType) : Click

            sealed interface Calendar : Click {

                data object StartDate : Calendar

                data object EndDate : Calendar

                data object Close : Calendar
            }
        }

        sealed interface Navigation : Action
    }

    sealed interface Event : Store.Event {

        data class HapticFeedback(
            val type: HapticFeedbackType,
        ) : Event
    }
}
