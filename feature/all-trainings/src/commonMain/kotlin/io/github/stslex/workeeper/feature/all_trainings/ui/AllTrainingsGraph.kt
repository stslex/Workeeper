// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsFeature
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsGraph
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.resources.Res
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_partial_format
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_bulk_archive_success
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Suppress("LongMethod")
fun NavGraphScope.allTrainingsGraph(
    factory: AllTrainingsGraph.Factory,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(AllTrainingsFeature(factory)) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowBulkDeleteSuccess ->
                    SnackbarManager.showSnackbar(
                        message = bulkArchiveMessage(event.archivedCount, event.blockedNames),
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

// TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
internal suspend fun bulkArchiveMessage(archivedCount: Int, blockedNames: List<String>): String =
    if (blockedNames.isEmpty()) {
        getPluralString(
            Res.plurals.feature_all_trainings_bulk_archive_success,
            archivedCount,
            archivedCount,
        )
    } else {
        getString(
            Res.string.feature_all_trainings_bulk_archive_partial_format,
            archivedCount,
            blockedNames.joinToString(", "),
        )
    }
