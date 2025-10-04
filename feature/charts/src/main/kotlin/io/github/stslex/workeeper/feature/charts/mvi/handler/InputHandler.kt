package io.github.stslex.workeeper.feature.charts.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    private val commonStore: CommonDataStore,
    store: ChartsHandlerStore,
) : Handler<Action.Input>, ChartsHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.ChangeStartDate -> processStartDateChange(action)
            is Action.Input.ChangeEndDate -> processEndDateChange(action)
            is Action.Input.Query -> processQuery(action)
            is Action.Input.ScrollToChart -> processScrollToChart(action)
        }
    }

    private fun processScrollToChart(action: Action.Input.ScrollToChart) {
        if (state.value.chartState.content?.selectedChartIndex != action.index) {
            sendEvent(ChartsStore.Event.HapticFeedback(HapticFeedbackType.VirtualKey))
        }
        updateState {
            it.copy(
                chartState = it.chartState.content
                    ?.copy(selectedChartIndex = action.index)
                    ?: it.chartState,
            )
        }
        sendEvent(ChartsStore.Event.OnChartTitleScrolled(action.index))
    }

    private fun processQuery(action: Action.Input.Query) {
        updateState { it.copy(name = action.name) }
    }

    private fun processStartDateChange(action: Action.Input.ChangeStartDate) {
        launch {
            commonStore.setHomeSelectedStartDate(action.timestamp)
        }
    }

    private fun processEndDateChange(action: Action.Input.ChangeEndDate) {
        launch {
            commonStore.setHomeSelectedEndDate(action.timestamp)
        }
    }
}
