// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsFeature
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event

private const val MAX_BLOCKED_NAMES = 2

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod")
fun NavGraphBuilder.allTrainingsGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(AllTrainingsFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        // Compose-ui in this project predates LocalResources; LocalContext.resources is
        // the documented v1 fallback. Plurals reload via Configuration changes through
        // recomposition, so the lint warning is benign here.
        @Suppress("LocalContextResourcesRead")
        val resources = LocalContext.current.resources
        val archivePartialFormat =
            stringResource(R.string.feature_all_trainings_bulk_archive_partial_format)

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowBulkArchiveSuccess ->
                    SnackbarManager.showSnackbar(
                        message = resources.getQuantityString(
                            R.plurals.feature_all_trainings_bulk_archive_success,
                            event.count,
                            event.count,
                        ),
                    )

                is Event.ShowBulkArchiveBlocked -> {
                    // TODO(tech-debt): Move blocked-name list shaping into handler/state and
                    // emit a ready-to-render localized message payload for UI.
                    val visible = event.blockedNames.take(MAX_BLOCKED_NAMES).joinToString(", ")
                    val joined = if (event.blockedNames.size > MAX_BLOCKED_NAMES) {
                        "$visible, +${event.blockedNames.size - MAX_BLOCKED_NAMES}"
                    } else {
                        visible
                    }
                    SnackbarManager.showSnackbar(
                        message = archivePartialFormat.format(event.archivedCount, joined),
                    )
                }

                is Event.ShowBulkDeleteSuccess ->
                    SnackbarManager.showSnackbar(
                        message = resources.getQuantityString(
                            R.plurals.feature_all_trainings_bulk_delete_success,
                            event.count,
                            event.count,
                        ),
                    )
            }
        }

        // Selection mode is the only state that intercepts the system back gesture, so the
        // bottom-tab predictive-back preview keeps running for the normal navigation case.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnSelectionExit)
        }

        AllTrainingsScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
