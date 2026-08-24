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

        // The plan editor returned. Forwarding is all this does: whether the result means
        // the session must be re-read is the Store's decision, not this composable's.
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

        // Driven by STATE, not by an event: a failure that resolves before this collector exists
        // would be dropped by a replay-free event flow, and the dropped case is the dangerous one
        // (see `State.loadFailed`). Both steps run here, in this order, so the message reaches the
        // app-scoped SnackbarManager — which outlives this destination — before the pop that
        // disposes this composition is asked for.
        if (processor.state.value.loadFailed) {
            LaunchedEffect(Unit) {
                SnackbarManager.showSnackbar(message = loadFailedMessage)
                processor.consume(Action.Navigation.Back)
            }
        }

        // §26's route gate. GUARD: this screen's emptiness predicate reads `exercises.isEmpty()`
        // and nothing else, so composing it before `isLoading` clears makes it assert "No exercises
        // yet" — headline, CTA and all — over a session that may be full. `loadFailed` is in the
        // predicate for the same reason: a failed load clears `isLoading`, and without this the
        // screen would render that same lie for as long as the pop takes.
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
