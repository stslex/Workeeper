// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.plan_editor.di.PlanEditorFeature
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event

/**
 * Registers the plan editor route. The screen carries unsaved-state interception via
 * `BackHandler(enabled = state.interceptBack)`; when intercepted, it dispatches
 * `Action.Click.OnBackClick` which the click handler routes to either Pop or
 * Discard-confirm-dialog depending on dirty state.
 */
fun NavGraphBuilder.planEditorGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(PlanEditorFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        val state by processor.state

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowError -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = context.getString(event.type.msgRes),
                    ),
                )
            }
        }

        BackHandler(enabled = state.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        PlanEditorScreen(
            modifier = modifier,
            state = state,
            consume = processor::consume,
        )
    }
}
