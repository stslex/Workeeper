// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithResults
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
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
                // Both steps here, in this order, and that is the point: the snackbar reaches the
                // app-scoped SnackbarManager — which outlives this destination — before the pop
                // that disposes this collector is asked for.
                is Event.LeaveWithError -> {
                    SnackbarManager.showSnackbar(message = event.message)
                    processor.consume(Action.Navigation.Back)
                }
            }
        }

        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        // §26's route gate. GUARD: this screen's emptiness predicate reads `exercises.isEmpty()`
        // and nothing else, so composing it before `isLoading` clears makes it assert "No exercises
        // yet" — headline, CTA and all — over a session that may be full. A blank frame is a screen
        // that has not spoken; an ungated one speaks and can be wrong.
        AppLoadedContent(isLoaded = processor.state.value.isLoading.not()) {
            LiveWorkoutScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
