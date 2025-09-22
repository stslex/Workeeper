package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store

import androidx.annotation.VisibleForTesting
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.all_exercises.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.all_exercises.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.handler.PagingHandler
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel([BaseStore::class])
@Qualifier(name = EXERCISE_SCOPE_NAME)
@Scope(name = EXERCISE_SCOPE_NAME)
internal class AllExercisesStoreImpl(
    @InjectedParam component: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    @Named(EXERCISE_SCOPE_NAME) storeEmitter: ExerciseHandlerStoreImpl,
    analytics: StoreAnalytics<Action, Event> = AnalyticsHolder.createStore(NAME),
    override val logger: Logger = storeLogger(NAME)
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = State.init(
        allItems = pagingHandler.processor
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
    analytics = analytics
) {


    companion object {

        @VisibleForTesting
        private const val NAME = "AllExercises"
    }
}