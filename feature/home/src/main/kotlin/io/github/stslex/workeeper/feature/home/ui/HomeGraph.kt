// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.start_mode.StartCardModeSheet
import io.github.stslex.workeeper.feature.home.di.HomeFeature
import io.github.stslex.workeeper.feature.home.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.components.TrainingPickerSheet

@Suppress("UnusedParameter")
fun NavGraphScope.homeGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(HomeFeature) { processor ->

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
            }
        }

        val state = processor.state.value

        HomeScreen(
            modifier = modifier,
            state = state,
            consume = processor::consume,
        )

        when (val sheet = state.bottomSheet) {
            BottomSheetState.Hidden -> Unit

            is BottomSheetState.TrainingPicker -> TrainingPickerSheet(
                state = sheet,
                onSelect = { uuid ->
                    processor.consume(Action.Click.OnPickerTrainingSelected(trainingUuid = uuid))
                },
                onStartBlank = { processor.consume(Action.Click.OnStartBlankClick) },
                onSeeAll = { processor.consume(Action.Click.OnPickerSeeAllClick) },
                onDismiss = { processor.consume(Action.Click.OnPickerDismiss) },
            )

            BottomSheetState.StartModePicker -> StartCardModeSheet(
                selected = state.startCardMode,
                onSelect = { mode -> processor.consume(Action.Click.OnModeSelected(mode)) },
                onDismiss = { processor.consume(Action.Click.OnModeSheetDismiss) },
            )
        }

        state.pendingConflict?.let { info ->
            ActiveSessionConflictDialog(
                activeSessionName = info.activeSessionName,
                progressLabel = info.progressLabel,
                onResume = { processor.consume(Action.Click.OnConflictResume) },
                onDeleteAndStartNew = { processor.consume(Action.Click.OnConflictDeleteAndStart) },
                onCancel = { processor.consume(Action.Click.OnConflictDismiss) },
            )
        }
    }
}
