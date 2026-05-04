// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingExerciseRow
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingHero
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingHistoryRow

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
    var menuExpanded by remember { mutableStateOf(false) }
    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        modifier = Modifier.testTag("TrainingDetailTopBar"),
        title = {
            Text(
                text = state.name,
                style = AppUi.typography.headlineSmall,
                color = AppUi.colors.textPrimary,
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("TrainingDetailBackButton"),
                onClick = { consume(Action.Click.OnBackClick) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feature_training_detail_back),
                )
            }
        },
        actions = {
            Box {
                IconButton(
                    modifier = Modifier.testTag("TrainingDetailMenuButton"),
                    onClick = { menuExpanded = true },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.feature_training_detail_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = AppUi.colors.surfaceTier2,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.feature_training_detail_edit),
                                style = AppUi.typography.bodyMedium,
                                color = AppUi.colors.textPrimary,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            consume(Action.Click.OnEditClick)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.feature_training_detail_archive),
                                style = AppUi.typography.bodyMedium,
                                color = AppUi.colors.setType.failureForeground,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            consume(Action.Click.OnArchiveClick)
                        },
                    )
                    if (state.canPermanentlyDelete) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("TrainingDetailPermanentDeleteMenuItem"),
                            text = {
                                Text(
                                    text = stringResource(R.string.feature_training_detail_permanent_delete),
                                    style = AppUi.typography.bodyMedium,
                                    color = AppUi.colors.setType.failureForeground,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                consume(Action.Click.OnPermanentDeleteClick)
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = AppUi.colors.surfaceTier0,
            scrolledContainerColor = AppUi.colors.surfaceTier0,
            titleContentColor = AppUi.colors.textPrimary,
            navigationIconContentColor = AppUi.colors.textPrimary,
            actionIconContentColor = AppUi.colors.textPrimary,
        ),
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
