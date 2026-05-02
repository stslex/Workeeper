// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import javax.inject.Inject

@ViewModelScoped
internal class ExerciseChartHandlerStoreImpl @Inject constructor() :
    ExerciseChartHandlerStore,
    BaseHandlerStore<State, Action, Event>()
