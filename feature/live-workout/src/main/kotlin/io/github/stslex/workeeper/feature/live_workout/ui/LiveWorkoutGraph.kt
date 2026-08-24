// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithResults
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutFeature
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphScope.liveWorkoutGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreenWithResults(LiveWorkoutFeature) { results, processor ->

        results.OnResult(Screen.PlanEditor::class) { saved ->
            processor.consume(Action.Common.PlanResultReceived(saved))
        }

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.HapticImpact -> haptic.performHapticFeedback(event.type)
                is Event.ShowSessionSavedSnackbar -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowError -> SnackbarManager.showSnackbar(message = event.message)
            }
        }

        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val loadFailedMessage = stringResource(R.string.feature_live_workout_error_session_load_failed)

        // GUARD: state, not an event (a replay-free flow would drop it), and the snackbar must
        // be shown before the pop. See documentation/architecture.md.
        if (processor.state.value.loadFailed) {
            LaunchedEffect(Unit) {
                SnackbarManager.showSnackbar(message = loadFailedMessage)
                processor.consume(Action.Navigation.Back)
            }
        }

        // GUARD: §26's route gate needs both flags — the screen's emptiness predicate is
        // `exercises.isEmpty()` alone, so composing early asserts an empty session.
        AppLoadedContent(
            isLoaded = with(processor.state.value) { isLoading.not() && loadFailed.not() },
        ) {
            LiveWorkoutScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
