// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesFeature
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter")
fun NavGraphScope.allExercisesGraph(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
) {
    navComponentScreen(AllExercisesFeature) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowPermanentDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowBulkDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)
            }
        }

        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnSelectionExit)
        }

        AllExercisesScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
