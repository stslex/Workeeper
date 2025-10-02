package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.AllExercisesComponent
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.State

@HiltViewModel(assistedFactory = AllExercisesStoreImpl.Factory::class)
internal class AllExercisesStoreImpl @AssistedInject constructor(
    @Assisted component: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ExerciseHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME),
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(
        allItems = pagingHandler.processor,
    ),
    storeEmitter = storeEmitter,
    storeDispatchers = storeDispatchers,
    handlerCreator = { action ->
        when (action) {
            is Action.Paging -> pagingHandler
            is Action.Navigation -> component
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    logger = logger,
    analytics = analytics,
) {

    @AssistedFactory
    interface Factory {
        fun create(component: AllExercisesComponent): AllExercisesStoreImpl
    }

    companion object {

        @VisibleForTesting
        private const val NAME = "AllExercises"
    }
}
