// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.rememberReorderableColumnState
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnDragHandle
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnItem
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import kotlinx.collections.immutable.persistentListOf

/**
 * One logged exercise. Disclosure follows the amended §7 model: [expanded] is exactly
 * membership in `State.expandedExerciseUuids`, the header is the toggle target, and this
 * composable renders the fact without owning it — the Store does (B8).
 */
@Composable
internal fun PastExerciseCard(
    exercise: PastExerciseUiModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onWeightChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onTypeChange: (String, SetTypeUiModel) -> Unit,
    onDragStarted: () -> Unit,
    onSetReorder: (performedExerciseUuid: String, from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ReorderableColumnState rather than ReorderableLazyColumn — the parent screen is
    // already a LazyColumn, so nesting another lazy scroller would break layout. The
    // non-lazy variant registers each row's measured Y bounds via onGloballyPositioned
    // and resolves the drop target against those bounds.
    val reorderState = rememberReorderableColumnState(
        onDragStarted = { onDragStarted() },
    ) { from, to ->
        onSetReorder(exercise.performedExerciseUuid, from, to)
    }
    AppCard(
        modifier = modifier.fillMaxWidth(),
        cardPadding = 0.dp,
    ) {
        LookaheadScope {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onHeaderClick)
                        .padding(AppDimension.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
                ) {
                    Text(
                        text = "${exercise.position + 1}.",
                        style = AppUi.typography.titleMedium,
                        color = AppUi.colors.textTertiary,
                    )
                    Text(
                        text = exercise.exerciseName,
                        style = AppUi.typography.titleMedium,
                        color = AppUi.colors.textPrimary,
                    )
                    if (exercise.skipped) {
                        Text(
                            text = stringResource(R.string.feature_past_session_skipped_chip),
                            style = AppUi.typography.labelSmall,
                            color = AppUi.colors.status.warning,
                        )
                    }
                }
                // Collapsed, the header row is the card's whole content. The v3 skin
                // (plan-line summary, static chevron) arrives with the C3 shell rebuild;
                // this commit is the behaviour alone.
                if (expanded && exercise.sets.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(horizontal = AppDimension.cardPadding),
                        text = stringResource(R.string.feature_past_session_no_sets),
                        style = AppUi.typography.bodyMedium,
                        color = AppUi.colors.textSecondary,
                    )
                } else if (expanded) {
                    exercise.sets.forEachIndexed { index, set ->
                        key(set.setUuid) {
                            PastSetEditRow(
                                set = set,
                                isWeighted = exercise.isWeighted,
                                onWeightChange = { raw -> onWeightChange(set.setUuid, raw) },
                                onRepsChange = { raw -> onRepsChange(set.setUuid, raw) },
                                onTypeChange = { type -> onTypeChange(set.setUuid, type) },
                                modifier = Modifier
                                    .reorderableColumnItem(
                                        state = reorderState,
                                        key = set.setUuid,
                                        index = index,
                                    )
                                    .padding(horizontal = AppDimension.cardPadding),
                                // Long-press the trailing drag-handle icon to start a drag.
                                // Skipped exercises stay read-only — handle is rendered for
                                // visual consistency but the gesture detector is disabled so a
                                // mis-targeted long-press cannot rewrite the historical record.
                                dragHandleModifier = Modifier.reorderableColumnDragHandle(
                                    state = reorderState,
                                    key = set.setUuid,
                                    enabled = !exercise.skipped,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppDimension.cardPadding))
            }
        }
    }
}

@Preview(name = "Light")
@Composable
private fun PastExerciseCardLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastExerciseCard(
            exercise = stubExercise(),
            expanded = true,
            onHeaderClick = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onTypeChange = { _, _ -> },
            onSetReorder = { _, _, _ -> },
            onDragStarted = { },
        )
    }
}

@Preview(name = "Dark")
@Composable
private fun PastExerciseCardDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastExerciseCard(
            exercise = stubExercise(),
            expanded = true,
            onHeaderClick = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onTypeChange = { _, _ -> },
            onSetReorder = { _, _, _ -> },
            onDragStarted = { },
        )
    }
}

@Preview(name = "Skipped")
@Composable
private fun PastExerciseCardSkippedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastExerciseCard(
            exercise = stubExercise().copy(skipped = true, sets = persistentListOf()),
            expanded = true,
            onHeaderClick = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onTypeChange = { _, _ -> },
            onSetReorder = { _, _, _ -> },
            onDragStarted = { },
        )
    }
}

private fun stubExercise(): PastExerciseUiModel = PastExerciseUiModel(
    performedExerciseUuid = "pe-1",
    exerciseName = "Bench press",
    position = 0,
    skipped = false,
    isWeighted = true,
    sets = persistentListOf(
        PastSetUiModel(
            setUuid = "s-1",
            performedExerciseUuid = "pe-1",
            position = 0,
            type = SetTypeUiModel.WORK,
            weightInput = "100",
            repsInput = "5",
            weightError = false,
            repsError = false,
            isPersonalRecord = true,
        ),
        PastSetUiModel(
            setUuid = "s-2",
            performedExerciseUuid = "pe-1",
            position = 1,
            type = SetTypeUiModel.WORK,
            weightInput = "100",
            repsInput = "5",
            weightError = false,
            repsError = false,
            isPersonalRecord = false,
        ),
    ),
)
