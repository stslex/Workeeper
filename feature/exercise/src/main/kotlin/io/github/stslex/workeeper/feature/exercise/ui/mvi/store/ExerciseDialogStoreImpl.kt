package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
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
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope

@KoinViewModel([BaseStore::class])
@Qualifier(name = EXERCISE_SCOPE_NAME)
@Scope(name = EXERCISE_SCOPE_NAME)
internal class ExerciseDialogStoreImpl(
    @InjectedParam component: ExerciseComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    commonHandler: CommonHandler,
    navigationHandler: NavigationHandler,
    dispatcher: AppDispatcher,
) : BaseStore<State, Action, Event>(
    name = "EXERCISE",
    appDispatcher = dispatcher,
    initialState = State.createInitial(component.data),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.Common -> commonHandler
            is Action.NavigationMiddleware -> navigationHandler
        }
    },
    initialActions = listOf(Action.Common.SearchTitle)
)
