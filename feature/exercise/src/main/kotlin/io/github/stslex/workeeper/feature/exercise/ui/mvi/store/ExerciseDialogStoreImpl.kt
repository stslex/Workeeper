package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.navigation.Screen.Exercise.Data
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@KoinViewModel([BaseStore::class])
@Scoped([ExerciseScope::class])
@Qualifier(ExerciseScope::class)
@Scope(ExerciseScope::class)
internal class ExerciseDialogStoreImpl(
    @InjectedParam component: ExerciseComponent,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    dispatcher: AppDispatcher
) : ExerciseHandlerStore, BaseStore<State, Action, Event, ExerciseHandlerStore>(
    name = "Exercise",
    appDispatcher = dispatcher,
    initialState = component.data?.mapToState() ?: State.INITIAL.copy(
        timestamp = System.currentTimeMillis(),
        initialHash = State.INITIAL.calculateEqualsHash
    ),
    handlerCreator = { action ->
        when (action) {
            is Action.Navigation -> component
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
        }
    },
)

private fun Data.mapToState(): State {
    val state = State.INITIAL.copy(
        uuid = uuid,
        name = State.INITIAL.name.update(name),
        sets = State.INITIAL.sets.update(sets.toString()),
        reps = State.INITIAL.reps.update(reps.toString()),
        weight = State.INITIAL.weight.update(weight.toString()),
        timestamp = timestamp,
        initialHash = 0
    )
    return state.copy(
        initialHash = state.calculateEqualsHash
    )
}