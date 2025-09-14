package io.github.stslex.workeeper.feature.all_exercises.di

import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.store.ExercisesStore.State
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [ExerciseHandlerStore::class, HandlerStoreEmitter::class])
@Scope(name = EXERCISE_SCOPE_NAME)
@Named(EXERCISE_SCOPE_NAME)
internal class ExerciseHandlerStoreImpl : ExerciseHandlerStore,
    BaseHandlerStore<State, Action, Event>()