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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordCard
import io.github.stslex.workeeper.core.ui.kit.components.pr.PrExplainerDialog
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip.Static
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHero
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHistoryRow
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseDetailScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPrExplainer by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("ExerciseDetailScreen"),
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
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Spacer(Modifier.height(AppDimension.Space.sm))
            // E3: hero only when a custom image is present. Placeholder dumbbell-icon
            // hero is gone — the type chip below carries the affordance instead.
            if (state.effectiveImageDisplay !is ImageDisplay.None) {
                ExerciseHero(
                    type = state.type,
                    imageDisplay = state.effectiveImageDisplay,
                    onImageClick = { consume(Action.Click.OnImageThumbnailClick) },
                )
            }
            // Inline type + tags row directly under the top app bar — replaces the
            // previous standalone Static + headline name combo (name lives in
            // LargeTopAppBar now).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
            ) {
                Static(label = stringResource(state.type.labelRes))
                state.tags.forEach { tag -> Static(label = tag.name) }
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
            if (state.planSummaryVisible) {
                state.adhocPlan?.let { plan ->
                    DefaultPlanCard(
                        plan = plan,
                        isWeighted = state.type == ExerciseTypeUiModel.WEIGHTED,
                    )
                }
            }
            state.personalRecord?.let { pr ->
                PersonalRecordCard(
                    modifier = Modifier.testTag("ExerciseDetailPersonalRecordCard"),
                    displayLabel = pr.displayLabel,
                    relativeDateLabel = pr.relativeDateLabel,
                    onClick = { consume(Action.Click.OnPrCardClick) },
                    onBadgeClick = { showPrExplainer = true },
                )
            }
            HistorySection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
    }
    if (showPrExplainer) {
        PrExplainerDialog(onDismiss = { showPrExplainer = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailLargeTopBar(
    state: State,
    consume: (Action) -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        modifier = Modifier.testTag("ExerciseDetailTopBar"),
        title = {
            Text(
                text = state.name,
                style = AppUi.typography.headlineSmall,
                color = AppUi.colors.textPrimary,
            )
        },
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
                    if (state.canPermanentlyDelete) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("ExerciseDetailPermanentDeleteMenuItem"),
                            text = {
                                Text(
                                    text = stringResource(
                                        R.string.feature_exercise_detail_permanent_delete,
                                    ),
                                    style = AppUi.typography.bodyMedium,
                                    color = AppUi.colors.setType.failureForeground,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                consume(Action.Click.OnPermanentDeleteMenuClick)
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
private fun DefaultPlanCard(
    plan: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
) {
    AppCard {
        Column(
            modifier = Modifier.testTag("ExerciseDetailDefaultPlanCard"),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            Text(
                text = stringResource(R.string.feature_exercise_detail_default_plan),
                style = AppUi.typography.labelSmall,
                color = AppUi.colors.textTertiary,
            )
            // Spec E4 grid: idx | weight | reps | type-chip with tabular-nums so the
            // numeric columns align across rows. Weight column collapses for weightless
            // exercises (no fallback "—" cell — matches LiveSetRow's conditional column).
            plan.forEachIndexed { index, set ->
                DefaultPlanRow(
                    index = index,
                    set = set,
                    isWeighted = isWeighted,
                )
            }
        }
    }
}

@Composable
private fun DefaultPlanRow(
    index: Int,
    set: PlanSetUiModel,
    isWeighted: Boolean,
) {
    val numberStyle = AppUi.typography.bodyMedium.copy(
        color = AppUi.colors.textPrimary,
        fontFeatureSettings = "tnum",
    )
    val indexStyle = AppUi.typography.bodyMedium.copy(
        color = AppUi.colors.textTertiary,
        fontFeatureSettings = "tnum",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ExerciseDetailDefaultPlanRow_$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.size(width = INDEX_COLUMN_WIDTH, height = ROW_HEIGHT),
            text = "${index + 1}.",
            style = indexStyle,
        )
        Spacer(Modifier.size(width = COLUMN_GAP_NUMERIC, height = ROW_HEIGHT))
        if (isWeighted) {
            Text(
                modifier = Modifier.weight(1f),
                text = formatWeightCell(set.weight),
                style = numberStyle,
            )
            Spacer(Modifier.size(width = COLUMN_GAP_NUMERIC, height = ROW_HEIGHT))
        }
        Text(
            modifier = Modifier.weight(1f),
            text = formatRepsCell(set.reps),
            style = numberStyle,
        )
        Spacer(Modifier.size(width = COLUMN_GAP_TO_CHIP, height = ROW_HEIGHT))
        AppSetTypeChip(type = set.type.toUiKitType())
    }
}

@Composable
private fun formatWeightCell(weight: Double?): String {
    val unit = stringResource(io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_plan_editor_unit_kg)
    if (weight == null) return ""
    val formatted = if (weight % 1.0 == 0.0) {
        weight.toLong().toString()
    } else {
        weight.toString().trimEnd('0').trimEnd('.')
    }
    return "$formatted $unit"
}

@Composable
private fun formatRepsCell(reps: Int): String {
    val unit = stringResource(io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_plan_editor_unit_reps)
    return "$reps $unit"
}

private val INDEX_COLUMN_WIDTH = 24.dp
private val ROW_HEIGHT = 28.dp
private val COLUMN_GAP_NUMERIC = 14.dp
private val COLUMN_GAP_TO_CHIP = 28.dp

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
