// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorFeature
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event

/**
 * Registers the two plan editor destinations:
 *
 *  - [Screen.PlanEditor.Existing] — DB-backed; persists `(type, plan)` to disk on Save
 *    and signals the caller via `planEditorSavedAttr`.
 *  - [Screen.PlanEditor.Draft] — in-memory; pops back with the draft as
 *    `PlanDraftResult` JSON via `planEditorDraftResultAttr` for the caller to merge into
 *    its local state.
 *
 * Two separate `composable<...>` blocks (rather than one with a polymorphic discriminator)
 * keep route resolution simple — typed-nav has known edge cases on sealed parents.
 */
fun NavGraphBuilder.planEditorGraph(
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.PlanEditor.Existing> { screen ->
        PlanEditorContent(modifier = modifier, screen = screen)
    }
    navScreen<Screen.PlanEditor.Draft> { screen ->
        PlanEditorContent(modifier = modifier, screen = screen)
    }
}

@Composable
private fun PlanEditorContent(
    modifier: Modifier,
    screen: Screen.PlanEditor,
) {
    val processor = PlanEditorFeature.processor(screen)
    val haptic = LocalHapticFeedback.current
    val state by processor.state
    // Pre-resolve error copy in composable scope so the suspend Handle lambda below
    // does not call `Context.getString` (Lint: LocalContextGetResourceValueCall).
    val loadFailedMessage = stringResource(ErrorType.LoadFailed.msgRes)
    val saveFailedMessage = stringResource(ErrorType.SaveFailed.msgRes)

    processor.Handle { event ->
        when (event) {
            is Event.HapticClick -> haptic.performHapticFeedback(event.type)
            is Event.ShowError -> SnackbarManager.showSnackbar(
                AppSnackbarModel(
                    message = when (event.type) {
                        ErrorType.LoadFailed -> loadFailedMessage
                        ErrorType.SaveFailed -> saveFailedMessage
                    },
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
