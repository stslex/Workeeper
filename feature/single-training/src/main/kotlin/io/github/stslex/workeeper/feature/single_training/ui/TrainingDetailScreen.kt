// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.DetailTopbar
import io.github.stslex.workeeper.core.ui.kit.components.topbar.TopbarAction
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingExerciseRow
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingHero
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingHistoryRow
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingDetailScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("TrainingDetailScreen"),
        topBar = {
            DetailLargeTopBar(
                state = state,
                consume = consume,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { DetailActionBar(state = state, consume = consume) },
        containerColor = AppUi.colors.surfaceTier0,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimension.screenEdge),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
        ) {
            Spacer(Modifier.height(AppDimension.Space.sm))
            // Name lives in the LargeTopAppBar now; the hero only carries description +
            // tag chips so the detail body is not duplicated against the collapsed bar.
            TrainingHero(
                description = state.description,
                tags = state.tags,
            )
            ExercisesSection(state = state, consume = consume)
            HistorySection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailLargeTopBar(
    state: State,
    consume: (Action) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val actions = remember(state.canPermanentlyDelete) {
        persistentListOf(
            TopbarAction(
                titleRes = R.string.feature_training_detail_edit,
                testTag = "TrainingDetailMenuButton",
                onClick = { consume(Action.Click.OnEditClick) },
            ),
            TopbarAction(
                titleRes = R.string.feature_training_detail_archive,
                testTag = "TrainingDetailArchiveMenuItem",
                onClick = { consume(Action.Click.OnArchiveClick) },
            ),
        ).apply {
            if (state.canPermanentlyDelete) {
                plus(
                    TopbarAction(
                        titleRes = R.string.feature_training_detail_permanent_delete,
                        testTag = "TrainingDetailPermanentDeleteMenuItem",
                        onClick = { consume(Action.Click.OnPermanentDeleteClick) },
                    )
                )
            }
        }
    }
    DetailTopbar(
        title = state.name,
        onBackIconClick = { consume(Action.Click.OnBackClick) },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ExercisesSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.feature_training_detail_exercises),
                style = AppUi.typography.labelSmall,
                color = AppUi.colors.textTertiary,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.feature_training_detail_exercise_count,
                    state.exercises.size,
                    state.exercises.size,
                ),
                style = AppUi.typography.labelSmall,
                color = AppUi.colors.textTertiary,
            )
        }
        state.exercises.forEach { exercise ->
            TrainingExerciseRow(
                item = exercise,
                onClick = { consume(Action.Click.OnExerciseRowClick(exercise.exerciseUuid)) },
            )
        }
    }
}

@Composable
private fun HistorySection(
    state: State,
    consume: (Action) -> Unit,
) {
    if (state.pastSessions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
        Text(
            text = stringResource(R.string.feature_training_detail_past_sessions),
            style = AppUi.typography.labelSmall,
            color = AppUi.colors.textTertiary,
        )
        state.pastSessions.forEach { session ->
            TrainingHistoryRow(
                item = session,
                onClick = { consume(Action.Click.OnPastSessionClick(session.sessionUuid)) },
            )
        }
    }
}

@Composable
private fun DetailActionBar(
    state: State,
    consume: (Action) -> Unit,
) {
    val isResume = state.activeSession != null && state.activeSession.trainingUuid == state.uuid
    val labelRes = if (isResume) {
        R.string.feature_training_detail_resume_session
    } else {
        R.string.feature_training_detail_start_session
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier0)
            .padding(AppDimension.screenEdge)
            .testTag("TrainingDetailActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("TrainingStartSessionButton"),
            text = stringResource(labelRes),
            onClick = { consume(Action.Click.OnStartSessionClick) },
            enabled = state.exercises.isNotEmpty(),
        )
    }
}
