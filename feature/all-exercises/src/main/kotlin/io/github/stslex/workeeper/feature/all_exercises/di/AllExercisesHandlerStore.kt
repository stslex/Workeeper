// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State

internal interface AllExercisesHandlerStore : HandlerStore<State, Action, Event>
