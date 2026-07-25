// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
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

// Metro constructs this Store (class-level @Inject). The Screen.LiveWorkout route arg is a bound
// instance on the extension factory (shape B), so it is an ordinary ctor param — no assisted machinery.
// Unscoped (ViewModelStore retention via rememberMetroStoreProcessor).
// The class is `public` (its accessor is on the public extension) but the primary constructor is
// `internal`, so the handler ctor params stay internal — :app calls the ctor at the IR level.
// NOTE: the route arg must be read HERE only; ScreenInjectionRule forbids injecting Screen elsewhere.
@Inject
class LiveWorkoutStoreImpl internal constructor(
    screen: Screen.LiveWorkout,
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

    companion object {

        @VisibleForTesting
        private const val NAME = "LiveWorkout"
    }
}
