// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorFeature
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorStoreProcessor
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event

/**
 * Registers the plan editor's one destination, [Screen.PlanEditor.Existing]. It uses [navScreen]
 * rather than `navComponentScreen` because route and feature type differ here. See architecture.md.
 */
fun NavGraphScope.planEditorGraph(
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.PlanEditor.Existing> { screen ->
        PlanEditorContent(
            modifier = modifier,
            processor = PlanEditorFeature.processor(screen),
        )
    }
}

@Composable
private fun PlanEditorContent(
    modifier: Modifier,
    processor: PlanEditorStoreProcessor,
) {
    val haptic = LocalHapticFeedback.current
    val state by processor.state
    // Resolved here because the suspend Handle lambda would call `Context.getString`
    // (Lint: LocalContextGetResourceValueCall).
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

    // The route does not compose until it has loaded (v3 redesign spec §26). GUARD: every path
    // that sets `isLoading` must clear it on failure too, or the screen stays empty forever.
    AppLoadedContent(isLoaded = state.isLoading.not()) {
        PlanEditorScreen(
            modifier = modifier,
            state = state,
            consume = processor::consume,
        )
    }
}
