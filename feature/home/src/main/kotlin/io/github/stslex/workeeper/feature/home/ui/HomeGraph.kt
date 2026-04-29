// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.home.di.HomeFeature
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.ui.components.TrainingPickerSheet

fun NavGraphBuilder.homeGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(HomeFeature) { processor ->

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowActiveSessionConflict -> Unit // rendered from state.pendingConflict
            }
        }

        val state = processor.state.value

        HomeScreen(
            modifier = modifier,
            state = state,
            consume = processor::consume,
            activeSessionModifier = with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState("activeSessionBanner"),
                    animatedVisibilityScope = this@navComponentScreen,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.FillBounds,
                        Alignment.Center,
                    ),
                )
            },
        )

        (state.picker as? State.PickerState.Visible)?.let { visible ->
            TrainingPickerSheet(
                state = visible,
                onSelect = { uuid ->
                    processor.consume(Action.Click.OnPickerTrainingSelected(trainingUuid = uuid))
                },
                onSeeAll = { processor.consume(Action.Click.OnPickerSeeAllClick) },
                onDismiss = { processor.consume(Action.Click.OnPickerDismiss) },
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
