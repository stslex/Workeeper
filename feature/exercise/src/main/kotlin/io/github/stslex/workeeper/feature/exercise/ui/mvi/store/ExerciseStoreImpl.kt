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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = ExerciseStoreImpl.Factory::class)
internal class ExerciseStoreImpl @AssistedInject constructor(
    @Assisted component: ExerciseComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    navigationHandler: NavigationHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ExerciseHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME),
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
            trainingUuid = component.trainingUuid,
        ),
    ),
    analytics = analytics,
    logger = logger,
) {

    @AssistedFactory
    interface Factory {
        fun create(component: ExerciseComponent): ExerciseStoreImpl
    }

    companion object {

        @VisibleForTesting
        private const val NAME = "Exercise"
    }
}
