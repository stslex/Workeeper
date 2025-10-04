package io.github.stslex.workeeper.feature.charts.mvi.store

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.charts.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.State

internal interface ChartsStore : Store<State, Action, Event> {

    data class State(
        val name: String,
        val chartState: ChartsState,
        val startDate: PropertyHolder.DateProperty,
        val endDate: PropertyHolder.DateProperty,
        val type: ChartsType,
        val calendarState: CalendarState,
    ) : Store.State {

        companion object {

            val INITIAL = State(
                name = "",
                chartState = ChartsState.Loading,
                startDate = PropertyHolder.DateProperty.new(
                    initialValue = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000),
                ), // 7 days default
                endDate = PropertyHolder.DateProperty.new(System.currentTimeMillis()),
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

            data class Query(val name: String) : Input

            data class CurrentChartPageChange(val index: Int) : Input
        }

        sealed interface Click : Action {

            data class ChangeType(val type: ChartsType) : Click

            data class ChartsHeader(val index: Int) : Click

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

        data class ScrollChartPager(
            val chartIndex: Int,
        ) : Event

        data class ScrollChartHeader(
            val chartIndex: Int,
            val animated: Boolean = true,
            val force: Boolean = false,
        ) : Event
    }
}
