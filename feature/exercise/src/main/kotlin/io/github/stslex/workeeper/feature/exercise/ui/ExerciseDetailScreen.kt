// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordCard
import io.github.stslex.workeeper.core.ui.kit.components.pr.PrExplainerDialog
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip.Static
import io.github.stslex.workeeper.core.ui.kit.components.topbar.DetailTopbar
import io.github.stslex.workeeper.core.ui.kit.components.topbar.TopbarAction
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHero
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHistoryRow
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    val actions = remember(state.canPermanentlyDelete) {
        exerciseDetailActions(state.canPermanentlyDelete, consume)
    }
    DetailTopbar(
        title = state.name,
        onBackIconClick = { consume(Action.Click.OnBackClick) },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Overflow-menu actions for the exercise detail screen. The permanent-delete entry is
 * appended only when [canPermanentlyDelete] is true (no history, no active templates).
 *
 * Built with [buildList] rather than `persistentListOf(...).apply { plus(...) }`: `apply`
 * returns its receiver and the `plus` result was discarded, so the permanent-delete item
 * never reached the menu — a silent regression this function makes testable.
 */
internal fun exerciseDetailActions(
    canPermanentlyDelete: Boolean,
    consume: (Action) -> Unit,
): ImmutableList<TopbarAction> = buildList {
    add(
        TopbarAction(
            titleRes = R.string.feature_exercise_detail_edit,
            testTag = "ExerciseDetailEditMenuItem",
            onClick = { consume(Action.Click.OnEditClick) },
        ),
    )
    add(
        TopbarAction(
            titleRes = R.string.feature_exercise_detail_archive,
            testTag = "ExerciseDetailArchiveMenuItem",
            onClick = { consume(Action.Click.OnArchiveMenuClick) },
        ),
    )
    if (canPermanentlyDelete) {
        add(
            TopbarAction(
                titleRes = R.string.feature_exercise_detail_permanent_delete,
                testTag = "ExerciseDetailPermanentDeleteMenuItem",
                onClick = { consume(Action.Click.OnPermanentDeleteMenuClick) },
            ),
        )
    }
}.toImmutableList()

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
            modifier = Modifier
                .width(INDEX_COLUMN_WIDTH)
                .align(Alignment.CenterVertically),
            text = "${index + 1}.",
            style = indexStyle,
            textAlign = TextAlign.Start,
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
    val unit =
        stringResource(io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_plan_editor_unit_kg)
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
    val unit =
        stringResource(io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_plan_editor_unit_reps)
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

private fun detailPreviewBaseState(): State = State
    .create(uuid = "preview-uuid")
    .copy(mode = State.Mode.Read, name = "Bench press", isLoading = false)

@Preview
@Composable
private fun ExerciseDetailScreenEmptyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseDetailScreen(
            state = detailPreviewBaseState(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseDetailScreenWithDescriptionAndTagsPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseDetailScreen(
            state = detailPreviewBaseState().copy(
                description = "Compound movement targeting chest, shoulders, and triceps.",
                tags = listOf(
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Chest"),
                ).toImmutableList(),
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseDetailScreenWithPlanAndPrPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseDetailScreen(
            state = detailPreviewBaseState().copy(
                adhocPlan = listOf(
                    PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WARMUP),
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 90.0, reps = 6, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 95.0, reps = 4, type = SetTypeUiModel.FAILURE),
                ).toImmutableList(),
                personalRecord = PersonalRecordUiModel(
                    sessionUuid = "s-pr",
                    displayLabel = "100 × 5",
                    relativeDateLabel = "Yesterday",
                ),
                tags = listOf(TagUiModel(uuid = "t1", name = "Push")).toImmutableList(),
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseDetailScreenWithHistoryPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseDetailScreen(
            state = detailPreviewBaseState().copy(
                recentHistory = listOf(
                    HistoryUiModel(
                        sessionUuid = "s1",
                        setsSummaryLabel = "80kg × 8 · 85kg × 6 · 90kg × 4",
                        metaLabel = "Yesterday · 3 sets",
                    ),
                    HistoryUiModel(
                        sessionUuid = "s2",
                        setsSummaryLabel = "75kg × 10 · 80kg × 8 · 80kg × 6",
                        metaLabel = "3 days ago · 3 sets",
                    ),
                ).toImmutableList(),
                personalRecord = PersonalRecordUiModel(
                    sessionUuid = "s-pr",
                    displayLabel = "100 × 5",
                    relativeDateLabel = "1 week ago",
                ),
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseDetailScreenWeightlessPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseDetailScreen(
            state = detailPreviewBaseState().copy(
                name = "Pull-ups",
                type = ExerciseTypeUiModel.WEIGHTLESS,
                description = "Bodyweight back exercise.",
                tags = listOf(TagUiModel(uuid = "t1", name = "Pull")).toImmutableList(),
                adhocPlan = listOf(
                    PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = null, reps = 10, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.FAILURE),
                ).toImmutableList(),
            ),
            consume = {},
        )
    }
}
