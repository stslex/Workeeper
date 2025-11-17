package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

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
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

@HiltViewModel(assistedFactory = ExerciseStoreImpl.Factory::class)
internal class ExerciseStoreImpl @AssistedInject constructor(
    @Assisted component: ExerciseComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    navigationHandler: NavigationHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ExerciseHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
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
            uuid = component.data.uuid,
            trainingUuid = component.data.trainingUuid,
        ),
    ),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<ExerciseComponent, ExerciseStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "Exercise"
    }
}
