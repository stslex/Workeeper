package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.annotation.VisibleForTesting
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel([BaseStore::class])
@Qualifier(name = EXERCISE_SCOPE_NAME)
@Scope(name = EXERCISE_SCOPE_NAME)
internal class ExerciseStoreImpl(
    @InjectedParam component: ExerciseComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    navigationHandler: NavigationHandler,
    storeDispatchers: StoreDispatchers,
    @Named(EXERCISE_SCOPE_NAME) storeEmitter: ExerciseHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME)
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.INITIAL,
    storeEmitter = storeEmitter,
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.Common -> commonHandler
            is Action.NavigationMiddleware -> navigationHandler
        }
    },
    storeDispatchers = storeDispatchers,
    initialActions = listOf(
        Action.Common.Init(
            uuid = component.uuid,
            trainingUuid = component.trainingUuid
        )
    ),
    analytics = analytics,
    logger = logger
) {

    companion object {

        @VisibleForTesting
        private const val NAME = "Exercise"
    }
}
