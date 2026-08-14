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
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionHeader
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingExerciseReadCard
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingHistoryRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.github.stslex.workeeper.core.ui.kit.R as KitR

/**
 * `v3-editors.md` §3.3's frame, in its order:
 *
 *     topbar  ‹ · h1.sm name · ⋮
 *     meta    tags, one line
 *     head    УПРАЖНЕНИЯ                3
 *     cards   collapsed cards: .ord + glyph + title + .plan-line + .chev   (ED9)
 *     head    ОПИСАНИЕ                                                     (D-OPEN-9, amended)
 *             description, absent when blank
 *     head    ИСТОРИЯ                   2 СЕССИИ
 *     list    ruled rows
 *     dock    Изменить (128dp) · Начать сессию | Продолжить сессию         (ED10)
 *
 * Two forms on one screen, on purpose (ED9): exercises are CARDS, history is a RULED LIST.
 * An exercise is a thing with contents; a session is an event — and two lists of identical
 * rows merge visually no matter how much air sits between them, which is the defect this
 * screen replaces.
 */
@Composable
internal fun TrainingDetailScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("TrainingDetailScreen"),
    ) {
        TopBar(state = state, consume = consume)
        Body(state = state, consume = consume)
    }
}

/**
 * `.topbar` (extraction §1.2 applied to §3.3's frame): back chevron · the training name at
 * `h1.sm` · the `⋮` opening the Store-homed detail-menu sheet. ED10 moved `Изменить` out of
 * that menu and into the dock; the menu keeps «В архив» and «Удалить навсегда».
 */
@Composable
private fun TopBar(
    state: State,
    consume: (Action) -> Unit,
) {
    AppTopBar(
        modifier = Modifier.testTag("TrainingDetailTopBar"),
        title = state.name,
        smallTitle = true,
        navigation = {
            AppIconButton(
                modifier = Modifier.testTag("TrainingDetailBackButton"),
                icon = AppIcons.ChevronLeft,
                contentDescription = stringResource(KitR.string.core_ui_kit_action_back),
                onClick = { consume(Action.Click.OnBackClick) },
            )
        },
        actions = {
            AppIconButton(
                modifier = Modifier.testTag("TrainingDetailMenuButton"),
                icon = AppIcons.MoreVertical,
                contentDescription = stringResource(R.string.feature_training_detail_more),
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
        ) {
            TagMetaLine(tags = state.tags.map { it.name })
            ExercisesSection(state = state, consume = consume)
            DescriptionSection(description = state.description)
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
 * §3.3's `meta` line — the tags, on ONE mono line, exactly as the exercise read screen draws
 * its own (§3.1): chips belong to pickers, a meta line is a statement.
 */
@Composable
private fun TagMetaLine(tags: List<String>) {
    if (tags.isEmpty()) return
    InGutter(top = AppDimension.Space.sm) {
        Text(
            modifier = Modifier.testTag("TrainingDetailTagMeta"),
            text = tags.joinToString(META_SEPARATOR),
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
 * §3.3 — `УПРАЖНЕНИЯ` with the bare count as its trailing label, over the collapsed cards
 * (ED9): the same form S4 shipped in the editor, minus the drag handle and `✕`, plus the
 * chevron — consumed from `TrainingExerciseCard.kt`, not rebuilt.
 */
@Composable
private fun ExercisesSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xxl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_training_detail_exercises),
            trailingLabel = state.exercises.size.toString(),
        )
        InGutter {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
                state.exercises.forEach { exercise ->
                    TrainingExerciseReadCard(
                        item = exercise,
                        onClick = {
                            consume(Action.Click.OnExerciseRowClick(exercise.exerciseUuid))
                        },
                    )
                }
            }
        }
    }
}

/**
 * §3.3 (as amended) — `ОПИСАНИЕ` between the cards and `ИСТОРИЯ`, the same slot the exercise
 * read screen gives it: D-OPEN-9's ruling is per-object, not per-screen — a description that
 * can be typed and never read is a field with no reader, wherever the field lives. The form
 * is the exercise block's own read-mode text half, description-only: a training has no image,
 * so the block reduces to its text. A section with nothing in it does not render.
 */
@Composable
private fun DescriptionSection(description: String) {
    if (description.isBlank()) return
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xxl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_training_edit_label_description),
        )
        InGutter {
            Text(
                modifier = Modifier.testTag("TrainingDescriptionText"),
                text = description,
                style = AppUi.typography.text.body,
                color = AppUi.colors.textSecondary,
            )
        }
    }
}

/**
 * §3.3 — the История section: head with the session count as its trailing label, then a
 * full-bleed `.list` ruled ABOVE the first row and BELOW every row — the exercise read
 * screen's own treatment (§3.5), deliberately N+1 rules (extraction C5; reported, not
 * resolved). ED9's second form: a ruled list, against the cards above it.
 *
 * **A section with nothing in it does not render** (S8): zero sessions is nothing to list,
 * so the head goes with the rows — the exercise read screen's own rule, per object.
 */
@Composable
private fun HistorySection(
    state: State,
    consume: (Action) -> Unit,
) {
    if (state.pastSessions.isEmpty()) return
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xxl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_training_detail_past_sessions),
            trailingLabel = state.historyCount
                .takeIf { it > 0 }
                ?.let { count ->
                    pluralStringResource(
                        R.plurals.feature_training_detail_session_count,
                        count,
                        count,
                    )
                },
        )
        Column {
            HistoryRule()
            state.pastSessions.forEach { session ->
                TrainingHistoryRow(
                    item = session,
                    onClick = { consume(Action.Click.OnPastSessionClick(session.sessionUuid)) },
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
 * `.dock` (§3.3, ED10): ghost `Изменить` at a fixed 128dp beside the primary session button
 * taking the rest — «Продолжить сессию» when the globally active session belongs to THIS
 * training, «Начать сессию» otherwise. Same gradient scrim as the exercise read screen's dock.
 */
@Composable
private fun Dock(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isResume = state.activeSession != null && state.activeSession.trainingUuid == state.uuid
    val labelRes = if (isResume) {
        R.string.feature_training_detail_resume_session
    } else {
        R.string.feature_training_detail_start_session
    }
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
            .testTag("TrainingDetailActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Ghost(
            modifier = Modifier
                .width(EDIT_BUTTON_WIDTH)
                .testTag("TrainingDetailEditButton"),
            text = stringResource(R.string.feature_training_detail_edit),
            onClick = { consume(Action.Click.OnEditClick) },
        )
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

/** `.dock`'s `linear-gradient(to top, base 62%, …)`: solid from the bottom 62%. */
private const val DOCK_GRADIENT_STOP = 0.38f

/** Clearance so the scroll content's tail clears the overlaid dock. */
private val DOCK_CLEARANCE = 104.dp

/** `Изменить` at `flex:0 0 130px` → the ladder-nearest fixed 128dp (§3.6, reused by §3.3). */
private val EDIT_BUTTON_WIDTH = 128.dp

private fun detailPreviewState(
    exercises: ImmutableList<TrainingExerciseItem>,
    sessions: ImmutableList<HistorySessionItem> = persistentListOf(),
): State = State
    .create(uuid = "preview-uuid")
    .copy(
        name = "День толчка",
        isLoading = false,
        exercises = exercises,
        pastSessions = sessions,
        historyCount = sessions.size,
    )

@Preview
@Composable
private fun TrainingDetailScreenPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        TrainingDetailScreen(
            state = detailPreviewState(
                exercises = listOf(
                    TrainingExerciseItem(
                        exerciseUuid = "1",
                        exerciseName = "Жим лёжа",
                        exerciseType = ExerciseTypeUiModel.WEIGHTED,
                        tags = persistentListOf("грудь"),
                        position = 0,
                        planSets = null,
                        planSummary = "60×10 · 80×8 · 90×6",
                    ),
                    TrainingExerciseItem(
                        exerciseUuid = "2",
                        exerciseName = "Подтягивания",
                        exerciseType = ExerciseTypeUiModel.WEIGHTLESS,
                        tags = persistentListOf(),
                        position = 1,
                        planSets = null,
                        planSummary = "",
                    ),
                ).toImmutableList(),
                sessions = listOf(
                    HistorySessionItem(sessionUuid = "s1", dateLabel = "27 июля 2026 г."),
                    HistorySessionItem(sessionUuid = "s2", dateLabel = "22 июля 2026 г."),
                ).toImmutableList(),
            ),
            consume = {},
        )
    }
}
