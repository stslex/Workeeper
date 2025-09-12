package io.github.stslex.workeeper.feature.exercise.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

internal interface ExerciseHandlerStore : HandlerStore<State, Action, Event>
