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
 * Registers the plan editor's one destination, [Screen.PlanEditor.Existing] — DB-backed, persists
 * `(type, plan)` to disk on Save and hands `true` back to the caller on the way out.
 *
 * **There is no creation destination.** An exercise with no persisted UUID is built on the exercise
 * form, which hosts `PlanEditorBody` inline; nothing routes here to make one.
 */
fun NavGraphBuilder.planEditorGraph(
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.PlanEditor.Existing> { screen ->
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

    // §26 "A route does not compose until it has loaded". Everything above this line still
    // runs while the load is in flight — the event Handle, the back interception — and only
    // the screen waits.
    //
    // It matters most on this route. `Screen.PlanEditor.Existing` seeds `type = WEIGHTED`
    // because the real value is on disk, and `CommonHandler.loadPlan` overwrites
    // `draft` / `type` / `initialType` / `initialDraft` unconditionally when the read lands.
    // Both are only safe because the seed is never seen and the window has no user in it: the
    // gate is what makes the unconditional write correct rather than merely unnoticed.
    //
    // Nothing is drawn instead, deliberately: neither mockup draws a loading surface, and
    // `AppNavigationHost` paints the background under every destination, so an unloaded route
    // is an empty frame in the app's own colour.
    //
    // LOAD-BEARING PRECONDITION: every path that sets `isLoading = true` must clear it on
    // FAILURE as well as on success. `HandlerStore.launch`/`launchDefault` default `onError`
    // to `{}` (B17, B21), so a throw that leaves the flag set is a permanently empty screen —
    // this gate is what gives that failure a cost. `CommonHandler.loadPlan` closes its own.
    if (state.isLoading) return

    PlanEditorScreen(
        modifier = modifier,
        state = state,
        consume = processor::consume,
    )
}
