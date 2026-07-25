// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Inject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

// Metro constructs this Store (class-level @Inject). The Screen.Exercise route arg is a bound instance
// on the extension factory (shape B), so it is an ordinary ctor param — no assisted machinery.
// Retention is owned by the Android ViewModelStore via rememberMetroStoreProcessor — no @SingleIn.
// The class is `public` (its accessor is on the public extension) but the primary constructor is
// `internal`, so the handler ctor params stay internal — :app calls the ctor at the IR level.
// NOTE: the route arg must be read HERE only; ScreenInjectionRule forbids injecting Screen elsewhere.
@Inject
class ExerciseStoreImpl internal constructor(
    screen: Screen.Exercise,
    navigationHandler: NavigationHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: ExerciseHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.create(uuid = screen.uuid),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> navigationHandler
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
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
        private const val NAME = "Exercise"
    }
}
