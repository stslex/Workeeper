// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartFeature
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event

fun NavGraphScope.exerciseChartGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ExerciseChartFeature) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
            }
        }

        // GUARD: NO route gate here, unlike the other five. The gate exists to stop a shell
        // asserting what it has not loaded, and this shell asserts nothing — its top bar carries no
        // title and its exercise header renders only inside `state.selectedExercise?.let`. Adding
        // one would cost a blank frame on a normal flow: `Content.Loading` is reachable with the
        // shell already on screen when the picker selects a new exercise out of an empty chart.
        ExerciseChartScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
