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

        // NO route gate here, unlike the other five, and the difference is this screen's shell:
        // its top bar carries no title and its exercise header renders only once an exercise is
        // known, so nothing on it can state something it has not loaded. Withholding the whole
        // screen would therefore buy nothing and cost a blank frame on picker reloads, where
        // `Content.Loading` is reachable with the shell already on screen. The spinner alone was
        // the flicker; `ChartContent` drops it and keeps the shell.
        ExerciseChartScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
