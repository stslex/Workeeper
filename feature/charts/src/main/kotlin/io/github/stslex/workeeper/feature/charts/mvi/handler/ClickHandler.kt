package io.github.stslex.workeeper.feature.charts.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Event
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    store: ChartsHandlerStore,
) : Handler<Action.Click>, ChartsHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.Calendar -> processClickCalendar(action)
            is Action.Click.ChangeType -> processTypeChange(action)
            is Action.Click.ChartsHeader -> processChartsHeaderClick(action)
        }
    }

    private fun processChartsHeaderClick(action: Action.Click.ChartsHeader) {
        updateState {
            it.copy(
                chartState = it.chartState.content
                    ?.copy(selectedChartIndex = action.index)
                    ?: it.chartState,
            )
        }
        sendEvent(Event.HapticFeedback(HapticFeedbackType.ContextClick))
        sendEvent(Event.ScrollChartPager(action.index))
    }

    private fun processTypeChange(action: Action.Click.ChangeType) {
        sendEvent(Event.HapticFeedback(HapticFeedbackType.ContextClick))
        updateState { it.copy(type = action.type) }
    }

    private fun processClickCalendar(action: Action.Click.Calendar) {
        sendEvent(Event.HapticFeedback(HapticFeedbackType.VirtualKey))

        val calendarState = when (action) {
            Action.Click.Calendar.Close -> CalendarState.Closed
            Action.Click.Calendar.EndDate -> CalendarState.Opened.EndDate
            Action.Click.Calendar.StartDate -> CalendarState.Opened.StartDate
        }

        updateState {
            it.copy(calendarState = calendarState)
        }
    }
}
