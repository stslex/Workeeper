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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordHero
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTag
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
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
        TopBar(state = state, consume = consume)
        Body(state = state, consume = consume)
    }
}

/**
 * `.topbar` (extraction §1.2 applied to §3.1's frame): back chevron hanging into the
 * gutter · the exercise name at `h1.sm` · the `⋮` overflow opening the Store-homed
 * detail-menu sheet. Internal so the goldens can render it in isolation, same move as
 * past-session's TopBar.
 */
@Composable
internal fun TopBar(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopBar(
        modifier = modifier.testTag("ExerciseDetailTopBar"),
        title = state.name,
        smallTitle = true,
        navigation = {
            AppIconButton(
                icon = AppIcons.ChevronLeft,
                contentDescription = stringResource(
                    io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_action_back,
                ),
                onClick = { consume(Action.Click.OnBackClick) },
            )
        },
        actions = {
            AppIconButton(
                icon = AppIcons.MoreVertical,
                contentDescription = stringResource(R.string.feature_exercise_detail_action_more),
                onClick = { consume(Action.Click.OnDetailMenuClick) },
            )
        },
    )
}

@Composable
private fun Body(
    state: State,
    consume: (Action) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Content scrolls out UNDER the dock; the clearance keeps the last block
                // reachable above it.
                .padding(bottom = DOCK_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Spacer(Modifier.height(AppDimension.Space.sm))
            // E3: hero only when a custom image is present (in code, not drawn in the
            // mockup — kept as shipped, see the PR delta table).
            if (state.effectiveImageDisplay !is ImageDisplay.None) {
                InGutter {
                    ExerciseHero(
                        type = state.type,
                        imageDisplay = state.effectiveImageDisplay,
                        onImageClick = { consume(Action.Click.OnImageThumbnailClick) },
                    )
                }
            }
            // §3.2 tag row: the type pill first, then the muscle-group tags — display only
            // (`cursor:default` in the mockup; the `.on` variant belongs to the chart).
            InGutter {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
                    verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                ) {
                    AppTag(label = stringResource(state.type.labelRes))
                    state.tags.forEach { tag -> AppTag(label = tag.name) }
                }
            }
            if (state.description.isNotBlank()) {
                InGutter {
                    AppCard {
                        Text(
                            text = state.description,
                            style = AppUi.typography.bodyMedium,
                            color = AppUi.colors.textPrimary,
                        )
                    }
                }
            }
            // §3.1 frame order: the record block sits above the default plan. The whole
            // hero is the chart entry point; the PR explainer moved to the history row's
            // record tag (the past-session pattern).
            state.personalRecord?.let { pr ->
                InGutter {
                    PersonalRecordHero(
                        modifier = Modifier.testTag("ExerciseDetailRecordHero"),
                        weightLabel = pr.weightLabel,
                        repsLabel = pr.repsLabel,
                        metaLabel = pr.absoluteDateLabel,
                        onClick = { consume(Action.Click.OnPrCardClick) },
                    )
                }
            }
            if (state.planSummaryVisible) {
                state.adhocPlan?.let { plan ->
                    InGutter {
                        DefaultPlanCard(
                            plan = plan,
                            isWeighted = state.type == ExerciseTypeUiModel.WEIGHTED,
                        )
                    }
                }
            }
            HistorySection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
        Dock(
            state = state,
            consume = consume,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Blocks carry the gutter individually so full-bleed sections can opt out. */
@Composable
private fun InGutter(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
        content()
    }
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
    Column(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
        Text(
            text = stringResource(R.string.feature_exercise_detail_recent),
            style = AppUi.typography.labelSmall,
            color = AppUi.colors.textTertiary,
        )
        Spacer(Modifier.height(AppDimension.Space.sm))
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
}

/**
 * `.dock` (§3.6): sticky at the bottom over a `linear-gradient(to top, base 62%,
 * transparent)` scrim, ghost `Изменить` at a fixed 128dp beside the primary
 * `Записать сейчас` taking the rest — same gradient mechanics as live-workout's dock.
 */
@Composable
private fun Dock(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = AppUi.colors.surfaceTier0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    DOCK_GRADIENT_STOP to base,
                    1f to base,
                ),
            )
            .padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                top = AppDimension.Space.lg,
                bottom = AppDimension.Space.xl,
            )
            .navigationBarsPadding()
            .testTag("ExerciseDetailDock"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Ghost(
            modifier = Modifier
                .width(EDIT_BUTTON_WIDTH)
                .testTag("ExerciseEditButton"),
            text = stringResource(R.string.feature_exercise_detail_edit),
            onClick = { consume(Action.Click.OnEditClick) },
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseTrackNowButton"),
            text = stringResource(R.string.feature_exercise_detail_track_now),
            onClick = { consume(Action.Click.OnTrackNowClick) },
            enabled = state.uuid != null,
        )
    }
}

/** `.dock`'s `linear-gradient(to top, base 62%, …)`: solid from the bottom 62%. */
private const val DOCK_GRADIENT_STOP = 0.38f

/** Clearance so the scroll content's tail clears the overlaid dock. */
private val DOCK_CLEARANCE = 104.dp

/** `Изменить` at `flex:0 0 130px` → the ladder-nearest fixed 128dp (§3.6). */
private val EDIT_BUTTON_WIDTH = 128.dp

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
                    weightLabel = "100",
                    repsLabel = "5",
                    absoluteDateLabel = "27 июля 2026 г.",
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
                    weightLabel = "100",
                    repsLabel = "5",
                    absoluteDateLabel = "21 июля 2026 г.",
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
