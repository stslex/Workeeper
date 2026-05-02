// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State

internal interface ExerciseChartHandlerStore : HandlerStore<State, Action, Event>
