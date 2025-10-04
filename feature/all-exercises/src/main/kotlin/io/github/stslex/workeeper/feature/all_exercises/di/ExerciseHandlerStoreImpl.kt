package io.github.stslex.workeeper.feature.all_exercises.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.State
import javax.inject.Inject

@ViewModelScoped
internal class ExerciseHandlerStoreImpl @Inject constructor() : ExerciseHandlerStore,
    BaseHandlerStore<State, Action, Event>()
