// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHero
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHistoryRow

@Composable
internal fun ExerciseDetailScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("ExerciseDetailScreen"),
    ) {
        DetailTopBar(consume = consume)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimension.screenEdge),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Spacer(Modifier.height(AppDimension.Space.sm))
            ExerciseHero(type = state.type)
            TypePill(state = state)
            Text(
                text = state.name,
                style = AppUi.typography.headlineSmall,
                color = AppUi.colors.textPrimary,
            )
            if (state.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                    verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
                ) {
                    state.tags.forEach { tag -> AppTagChip.Static(label = tag.name) }
                }
            }
            if (state.description.isNotBlank()) {
                AppCard {
                    Text(
                        text = state.description,
                        style = AppUi.typography.bodyMedium,
                        color = AppUi.colors.textPrimary,
                    )
                }
            }
            HistorySection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
        DetailActionBar(state = state, consume = consume)
    }
}

@Composable
private fun DetailTopBar(consume: (Action) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    AppTopAppBar(
        title = "",
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("ExerciseDetailBackButton"),
                onClick = { consume(Action.Click.OnBackClick) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feature_exercise_detail_back_description),
                )
            }
        },
        actions = {
            Box {
                IconButton(
                    modifier = Modifier.testTag("ExerciseDetailMenuButton"),
                    onClick = { menuExpanded = true },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(
                            R.string.feature_exercise_detail_more_description,
                        ),
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
                                text = stringResource(R.string.feature_exercise_detail_edit),
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
                                text = stringResource(R.string.feature_exercise_detail_archive),
                                style = AppUi.typography.bodyMedium,
                                color = AppUi.colors.setType.failureForeground,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            consume(Action.Click.OnArchiveMenuClick)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun TypePill(state: State) {
    val labelRes = if (state.type == ExerciseTypeDataModel.WEIGHTED) {
        R.string.feature_exercise_detail_type_weighted
    } else {
        R.string.feature_exercise_detail_type_weightless
    }
    AppTagChip.Static(label = stringResource(labelRes))
}

@Composable
private fun HistorySection(
    state: State,
    consume: (Action) -> Unit,
) {
    Text(
        text = stringResource(R.string.feature_exercise_detail_recent),
        style = AppUi.typography.labelSmall,
        color = AppUi.colors.textTertiary,
    )
    if (state.recentHistory.isEmpty()) {
        Text(
            text = stringResource(R.string.feature_exercise_detail_no_history),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            state.recentHistory.forEach { history ->
                ExerciseHistoryRow(
                    item = history,
                    onClick = { consume(Action.Click.OnHistoryRowClick(history.sessionUuid)) },
                )
            }
        }
    }
}

@Composable
private fun DetailActionBar(
    state: State,
    consume: (Action) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier0)
            .padding(AppDimension.screenEdge)
            .testTag("ExerciseDetailActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseTrackNowButton"),
            text = stringResource(R.string.feature_exercise_detail_track_now),
            onClick = { consume(Action.Click.OnTrackNowClick) },
            enabled = state.uuid != null,
        )
        AppButton.Secondary(
            modifier = Modifier.testTag("ExerciseEditButton"),
            text = stringResource(R.string.feature_exercise_detail_edit),
            onClick = { consume(Action.Click.OnEditClick) },
        )
    }
}
