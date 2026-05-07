// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.getStateFlow
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithState
import io.github.stslex.workeeper.core.ui.mvi.setAttrDefaultValue
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutFeature
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event

@Suppress("LongMethod", "CyclomaticComplexMethod", "UnusedParameter")
fun NavGraphBuilder.liveWorkoutGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreenWithState(LiveWorkoutFeature) { stateHandle, processor ->

        val attrValue by stateHandle
            .getStateFlow(Screen.PlanEditor.planEditorSavedAttr)
            .collectAsState()

        LaunchedEffect(attrValue) {
            if (attrValue == true) {
                processor.consume(Action.Common.Reload)
                stateHandle.setAttrDefaultValue(Screen.PlanEditor.planEditorSavedAttr)
            }
        }

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            Log.tag("MVI_STORE_LiveWorkout").i { "Received event: $event" }
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

        LiveWorkoutScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
