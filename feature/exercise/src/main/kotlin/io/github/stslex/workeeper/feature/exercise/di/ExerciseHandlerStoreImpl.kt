package io.github.stslex.workeeper.feature.exercise.di

import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [ExerciseHandlerStore::class, HandlerStoreEmitter::class])
@Scope(name = EXERCISE_SCOPE_NAME)
@Named(EXERCISE_SCOPE_NAME)
internal class ExerciseHandlerStoreImpl : ExerciseHandlerStore,
    BaseHandlerStore<State, Action, Event>()