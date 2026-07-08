// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStoreImpl
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.DialogClickHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State

// Metro assisted Store: @AssistedInject + @Assisted screen; graph exposes the @AssistedFactory
// (never the Store). No @HiltViewModel; unscoped (ViewModelStore retention via rememberMetroStoreProcessor).
@AssistedInject
internal class LiveWorkoutStoreImpl(
    @Assisted screen: Screen.LiveWorkout,
    navigationHandler: NavigationHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    dialogClickHandler: DialogClickHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: LiveWorkoutHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(
        sessionUuid = screen.sessionUuid,
        trainingUuid = screen.trainingUuid,
    ),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.DialogClick -> dialogClickHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<Screen.LiveWorkout, LiveWorkoutStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "LiveWorkout"
    }
}
