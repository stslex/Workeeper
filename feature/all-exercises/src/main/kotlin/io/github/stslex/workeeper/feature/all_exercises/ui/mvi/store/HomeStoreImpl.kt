package io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store

import io.github.stslex.workeeper.core.ui.mvi.BaseStore
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
internal class HomeStoreImpl(
    @InjectedParam component: NavigationHandler,
    pagingHandler: PagingHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    storeDispatchers: StoreDispatchers,
    @Named(EXERCISE_SCOPE_NAME) storeEmitter: ExerciseHandlerStoreImpl
) : BaseStore<State, Action, Event>(
    name = "AllExercises",
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
    initialActions = listOf(Action.Paging.Init)
)