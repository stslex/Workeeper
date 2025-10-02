package io.github.stslex.workeeper.feature.exercise.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import javax.inject.Inject

@ViewModelScoped
internal class ExerciseHandlerStoreImpl @Inject constructor() : ExerciseHandlerStore,
    BaseHandlerStore<State, Action, Event>()
