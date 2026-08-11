// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordHero
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionHeader
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.PlanSetCard
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDescriptionBlock
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHistoryRow
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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

/**
 * `v3-editors.md` §3.1's frame, in its order: tags · record · plan · description · history.
 *
 * The **description** sits after the plan and before the history — ED3's order, where the
 * description follows the screen's main slot — and it carries the image (D-OPEN-9). The **plan**
 * is the set-row card the editor draws (ED2). Both are the frame's placements and not this
 * file's: move either and this screen stops agreeing with the editor.
 */
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
        ) {
            TagMetaLine(tags = state.tags)
            // §3.1 frame order: the record block sits above the default plan (`.prhero`'s
            // 6px top margin lands on the tag row's 16px bottom — the lg rung). The whole
            // hero is the chart entry point; the PR explainer moved to the history row's
            // record tag (the past-session pattern).
            state.personalRecord?.let { pr ->
                InGutter(top = AppDimension.Space.lg) {
                    PersonalRecordHero(
                        modifier = Modifier.testTag("ExerciseDetailRecordHero"),
                        weightLabel = pr.weightLabel,
                        repsLabel = pr.repsLabel,
                        metaLabel = pr.absoluteDateLabel,
                        onClick = { consume(Action.Click.OnPrCardClick) },
                    )
                }
            }
            DefaultPlanSection(state = state)
            DescriptionSection(state = state, consume = consume)
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
private fun InGutter(
    top: Dp = AppDimension.Space.none,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.padding(
            start = AppDimension.screenEdge,
            end = AppDimension.screenEdge,
            top = top,
        ),
    ) {
        content()
    }
}

/**
 * §3.1's `meta` line — the tags, on ONE mono line, and **the exercise type is not among them**
 * (ED12). The type is a property of the exercise and is declared once, on the plan head, where it
 * explains the shape of the rows underneath it; a chip here would read as a tag the user applied.
 *
 * The separator is the app's own meta separator — `ExerciseHistoryRow`'s set summary and
 * `.prhero`'s date line both use it — so a meta line looks like a meta line wherever it appears.
 */
@Composable
private fun TagMetaLine(tags: ImmutableList<AppTagItem>) {
    if (tags.isEmpty()) return
    InGutter(top = AppDimension.Space.sm) {
        Text(
            modifier = Modifier.testTag("ExerciseDetailTagMeta"),
            text = tags.joinToString(META_SEPARATOR) { it.name },
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `.meta`'s own separator — the middle dot with air either side, as every meta line draws it. */
private const val META_SEPARATOR = " · "

/**
 * §3.1 — `ПЛАН ПО УМОЛЧАНИЮ` with the type as its trailing label (ED12), over the read-only
 * [PlanSetCard] (ED2). Read and edit draw the same card by ruling (D-OPEN-6).
 *
 * **A section with nothing in it does not render** — the read frame's one rule (S8, the same
 * rule the description block and the history follow). The empty card — head plus a setbar whose
 * «− подход» is disabled — belongs to the editor, where the setbar is a target; read has no
 * target. The type declaration (ED12) leaves with the head: it explains the shape of the rows
 * underneath it, and with no rows there is nothing to explain.
 */
@Composable
private fun DefaultPlanSection(state: State) {
    if (state.adhocPlan.isNullOrEmpty()) return
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xxl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_exercise_detail_default_plan),
            trailingLabel = stringResource(state.type.labelRes),
        )
        InGutter {
            PlanSetCard(
                modifier = Modifier.testTag("ExerciseDetailDefaultPlanCard"),
                plan = state.adhocPlan ?: persistentListOf(),
                isWeighted = state.type == ExerciseTypeUiModel.WEIGHTED,
            )
        }
    }
}

/**
 * §3.1 — `ОПИСАНИЕ` over the block that carries the description and the picture beside it
 * (D-OPEN-9). The block is built once and the editor consumes the same one; the head stays with
 * the host, whose section rhythm differs from a form's.
 *
 * Read has no picker: an image chosen here would be a `pendingImage` on a screen with no Save.
 * So the block gets the viewer and nothing else.
 *
 * **A section with nothing in it does not render.** Text OR picture puts it on the screen;
 * neither takes the head away with it and `ИСТОРИЯ` follows the plan card directly. The
 * disjunction is the point and an `&&` would be a different screen: a description with no photo,
 * and a photo with no description, are both worth a section. **The dashed placeholder belongs to
 * the editor**, where it is a target you can tap; on read it announces that there is nothing to
 * announce, which is why the whole section leaves rather than the box alone.
 */
@Composable
private fun DescriptionSection(
    state: State,
    consume: (Action) -> Unit,
) {
    if (state.description.isBlank() && state.effectiveImageDisplay is ImageDisplay.None) return
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xxl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_exercise_edit_label_description),
        )
        InGutter {
            ExerciseDescriptionBlock(
                description = state.description,
                type = state.type,
                imageDisplay = state.effectiveImageDisplay,
                onOpenImage = { consume(Action.Click.OnImageThumbnailClick) },
            )
        }
    }
}

/**
 * §3.5 — the История section: head with the session count as its trailing label, then a
 * full-bleed `.list` ruled ABOVE the first row and BELOW every row (`hair-s` →
 * borderDefault) — deliberately N+1 rules, the drawn conflict with `AppSection`'s
 * between-only rule (extraction C5; reported, not resolved). The record row's trailing
 * tag replaces the chevron and opens the PR explainer.
 *
 * **A section with nothing in it does not render** (S8): zero sessions is nothing to list,
 * so the head goes with the rows and the screen is shorter.
 */
@Composable
private fun HistorySection(
    state: State,
    consume: (Action) -> Unit,
) {
    if (state.recentHistory.isEmpty()) return
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xxl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_exercise_detail_recent),
            trailingLabel = state.historyCount
                .takeIf { it > 0 }
                ?.let { count ->
                    pluralStringResource(
                        R.plurals.feature_exercise_detail_history_count,
                        count,
                        count,
                    )
                },
        )
        Column {
            HistoryRule()
            state.recentHistory.forEach { history ->
                ExerciseHistoryRow(
                    item = history,
                    isRecord = history.sessionUuid == state.personalRecord?.sessionUuid,
                    onClick = { consume(Action.Click.OnHistoryRowClick(history.sessionUuid)) },
                    onPrTagClick = { consume(Action.Click.OnHistoryPrTagClick) },
                )
                HistoryRule()
            }
        }
    }
}

/** `.list`/`.row` rule — 1px solid `hair-s` (borderDefault), full bleed. */
@Composable
private fun HistoryRule() {
    HorizontalDivider(
        thickness = AppDimension.Border.small,
        color = AppUi.colors.borderDefault,
    )
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
                    AppTagItem(uuid = "t1", name = "Push"),
                    AppTagItem(uuid = "t2", name = "Chest"),
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
                tags = listOf(AppTagItem(uuid = "t1", name = "Push")).toImmutableList(),
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
                historyCount = 4,
                recentHistory = listOf(
                    HistoryUiModel(
                        sessionUuid = "s1",
                        dateLabel = "27 июля",
                        setsSummaryLabel = "80×8 · 85×6 · 90×4",
                    ),
                    HistoryUiModel(
                        sessionUuid = "s2",
                        dateLabel = "25 июля",
                        setsSummaryLabel = "75×10 · 80×8 · 80×6",
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
                tags = listOf(AppTagItem(uuid = "t1", name = "Pull")).toImmutableList(),
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
