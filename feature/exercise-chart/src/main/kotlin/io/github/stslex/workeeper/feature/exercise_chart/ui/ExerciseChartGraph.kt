// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartFeature
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event

fun NavGraphBuilder.exerciseChartGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ExerciseChartFeature) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
            }
        }

        ExerciseChartScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
