package io.github.stslex.workeeper.feature.all_exercises.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.State

internal interface ExerciseHandlerStore : HandlerStore<State, Action, Event>
