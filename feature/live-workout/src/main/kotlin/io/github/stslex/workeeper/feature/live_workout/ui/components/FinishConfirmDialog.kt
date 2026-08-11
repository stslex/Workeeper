// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppFieldLabel
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordBadge
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun FinishConfirmDialog(
    stats: DialogState.FinishSession,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(R.string.feature_live_workout_finish_title),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.feature_live_workout_finish_body),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            if (stats.requiresName) {
                FinishNameField(
                    name = stats.nameDraft,
                    label = stats.nameLabel,
                    placeholder = stats.namePlaceholder,
                    error = stats.nameError,
                    onNameChange = onNameChange,
                )
            }
            StatRow(
                label = stringResource(R.string.feature_live_workout_finish_stat_duration),
                value = stats.durationLabel,
            )
            StatRow(
                label = stringResource(R.string.feature_live_workout_finish_stat_exercises),
                value = stats.exercisesSummaryLabel,
            )
            StatRow(
                label = stringResource(R.string.feature_live_workout_finish_stat_sets),
                value = stats.setsLoggedLabel,
            )
            if (stats.unfilledSetCount > 0) {
                // Stated, never silent (§6.1). An empty row can hold no record and displays
                // identically to a deliberate zero, so discarding is safe — but the user
                // still gets told it is happening before they commit.
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_live_workout_finish_unfilled_discarded,
                        stats.unfilledSetCount,
                        stats.unfilledSetCount,
                    ),
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
            }
            if (stats.newPersonalRecords.isNotEmpty()) {
                NewPersonalRecordsSection(records = stats.newPersonalRecords)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = AppDimension.Space.sm,
                    alignment = Alignment.End,
                ),
            ) {
                AppButton.Tertiary(
                    text = stringResource(R.string.feature_live_workout_finish_keep_going),
                    onClick = onDismiss,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Primary(
                    text = stringResource(R.string.feature_live_workout_finish_confirm),
                    onClick = onConfirm,
                    enabled = stats.confirmEnabled,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Composable
private fun FinishNameField(
    name: String,
    label: String,
    placeholder: String,
    error: String?,
    onNameChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs)) {
        // `.flabel` — the label sits above the box, because M3's floating one is drawn nowhere
        // and `AppTextField` therefore has no `label` parameter (§26, "The editors' text field").
        AppFieldLabel(text = label)
        AppTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = placeholder,
            isError = error != null,
            // Same string the `.flabel` above draws — the drawn label is a sibling node and does
            // not name the field to a screen reader.
            accessibilityLabel = label,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
        )
        if (error != null) {
            Text(
                text = error,
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.status.error,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        Text(
            text = value,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
    }
}

@Composable
private fun NewPersonalRecordsSection(
    records: ImmutableList<DialogState.FinishSession.NewPrEntry>,
) {
    val palette = AppUi.colors.record
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.background)
            .border(width = AppDimension.Border.small, color = palette.border, shape = shape)
            .padding(AppDimension.Space.md),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            PersonalRecordBadge()
            Text(
                text = pluralStringResource(
                    R.plurals.feature_live_workout_finish_new_pr_title,
                    records.size,
                    records.size,
                ),
                style = AppUi.typography.titleSmall,
                color = palette.textPrimary,
            )
        }
        records.forEach { entry ->
            Text(
                text = stringResource(
                    R.string.feature_live_workout_finish_new_pr_row_format,
                    entry.exerciseName,
                    entry.displayLabel,
                ),
                style = AppUi.typography.bodyMedium,
                color = palette.textSecondary,
            )
        }
    }
}

@Preview
@Composable
private fun FinishConfirmDialogAllDoneLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        FinishConfirmDialog(
            stats = DialogState.FinishSession(
                durationMillis = 47 * 60_000L + 8_000L,
                durationLabel = "47:08",
                exercisesSummaryLabel = "5 of 5 done",
                setsLoggedLabel = "22",
                newPersonalRecords = persistentListOf(),
            ),
            onNameChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun FinishConfirmDialogPartialDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        FinishConfirmDialog(
            stats = DialogState.FinishSession(
                durationMillis = 32 * 60_000L,
                durationLabel = "32:00",
                exercisesSummaryLabel = "3 of 5 done · 1 skipped",
                setsLoggedLabel = "12",
                newPersonalRecords = persistentListOf(),
            ),
            onNameChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun FinishConfirmDialogWithRecordsPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        FinishConfirmDialog(
            stats = DialogState.FinishSession(
                durationMillis = 47 * 60_000L,
                durationLabel = "47:00",
                exercisesSummaryLabel = "5 of 5 done",
                setsLoggedLabel = "22",
                newPersonalRecords = listOf(
                    DialogState.FinishSession.NewPrEntry(
                        exerciseUuid = "ex-1",
                        exerciseName = "Bench press",
                        displayLabel = "105 × 5",
                    ),
                    DialogState.FinishSession.NewPrEntry(
                        exerciseUuid = "ex-2",
                        exerciseName = "Pull-ups",
                        displayLabel = "15 reps",
                    ),
                ).toImmutableList(),
            ),
            onNameChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
