// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsFeature
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod")
fun NavGraphBuilder.allTrainingsGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(AllTrainingsFeature) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowBulkArchiveSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)

                is Event.ShowBulkArchiveBlocked ->
                    SnackbarManager.showSnackbar(message = event.message)

                is Event.ShowBulkDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)
            }
        }

        // Selection mode is the only state that intercepts the system back gesture, so the
        // bottom-tab predictive-back preview keeps running for the normal navigation case.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnSelectionExit)
        }

        AllTrainingsScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
