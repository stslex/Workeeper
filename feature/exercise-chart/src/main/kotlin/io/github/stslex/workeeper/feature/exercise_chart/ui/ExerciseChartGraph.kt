// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartFeature
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore
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

        // Same centred spinner, same flicker as `past-session`, same gate — but scoped to the
        // COLD open only, which `Content.Loading` already means by construction: a reload with a
        // drawable dataset resolves to `Content.Plot` and a reload that resolves to nothing lands
        // on `Content.Empty`, so neither passes through here. The store's KDoc keeps its rule
        // that `isLoading` does not participate in the verdict; this reads the verdict, not the
        // flag.
        AppLoadedContent(
            isLoaded = processor.state.value.content !is ExerciseChartStore.Content.Loading,
        ) {
            ExerciseChartScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
