// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.card.AppCard
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun PastExerciseCard(
    exercise: PastExerciseUiModel,
    onWeightChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onTypeChange: (String, SetTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            if (exercise.sets.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_past_session_no_sets),
                    style = AppUi.typography.bodyMedium,
                    color = AppUi.colors.textSecondary,
                )
            } else {
                exercise.sets.forEach { set ->
                    PastSetEditRow(
                        set = set,
                        isWeighted = exercise.isWeighted,
                        onWeightChange = { raw -> onWeightChange(set.setUuid, raw) },
                        onRepsChange = { raw -> onRepsChange(set.setUuid, raw) },
                        onTypeChange = { type -> onTypeChange(set.setUuid, type) },
                    )
                }
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
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onTypeChange = { _, _ -> },
        )
    }
}

@Preview(name = "Dark")
@Composable
private fun PastExerciseCardDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastExerciseCard(
            exercise = stubExercise(),
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onTypeChange = { _, _ -> },
        )
    }
}

@Preview(name = "Skipped")
@Composable
private fun PastExerciseCardSkippedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastExerciseCard(
            exercise = stubExercise().copy(skipped = true, sets = persistentListOf()),
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onTypeChange = { _, _ -> },
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
        ),
    ),
)
