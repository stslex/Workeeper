package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Event
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [ClickHandler::class])
@Scope(name = CHARTS_SCOPE_NAME)
internal class ClickHandler(
    @Named(CHARTS_SCOPE_NAME) store: ChartsHandlerStore,
) : Handler<Action.Click>, ChartsHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.Calendar -> processClickCalendar(action)
            is Action.Click.ChangeType -> processTypeChange(action)
        }
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
