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
            }
        }

        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        // §26's route gate, arriving here late and for a measured reason. Composed eagerly, this
        // screen asserted "No exercises yet" — headline, supporting line and an Add CTA — over a
        // session that had thirteen exercises and sixty sets, because the emptiness predicate read
        // `exercises.isEmpty()` while `isLoading` sat in the same State unread. A blank frame is a
        // screen that has not spoken; that was a screen that spoke and was wrong.
        AppLoadedContent(isLoaded = processor.state.value.isLoading.not()) {
            LiveWorkoutScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
