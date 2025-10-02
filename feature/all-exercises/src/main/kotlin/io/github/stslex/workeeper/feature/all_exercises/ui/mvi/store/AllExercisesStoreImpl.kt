package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store

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
    @Assisted component: AllExercisesComponent,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    storeEmitter: ExerciseHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
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
            is Action.Navigation -> component as NavigationHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
    loggerHolder = loggerHolder,
    analyticsHolder = analyticsHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<AllExercisesComponent, AllExercisesStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "AllExercises"
    }
}
