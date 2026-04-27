// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

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
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStoreImpl
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.LiveWorkoutComponent
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.PlanEditActionHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State

@HiltViewModel(assistedFactory = LiveWorkoutStoreImpl.Factory::class)
internal class LiveWorkoutStoreImpl @AssistedInject constructor(
    @Assisted component: LiveWorkoutComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    planEditActionHandler: PlanEditActionHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: LiveWorkoutHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(
        sessionUuid = component.data.sessionUuid,
        trainingUuid = component.data.trainingUuid,
    ),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component as NavigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.PlanEditAction -> planEditActionHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<LiveWorkoutComponent, LiveWorkoutStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "LiveWorkout"
    }
}
