// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStoreImpl
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.ChartComponent
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State

@HiltViewModel(assistedFactory = ExerciseChartStoreImpl.Factory::class)
internal class ExerciseChartStoreImpl @AssistedInject constructor(
    @Assisted component: ChartComponent,
    clickHandler: ClickHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: ExerciseChartHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(initialUuid = component.data.exerciseUuid),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component as NavigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<ChartComponent, ExerciseChartStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "ExerciseChart"
    }
}
