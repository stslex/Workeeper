// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.core.ui.kit.R as KitR

/**
 * The plan head's `(i)` sheet (ED8): what a default plan is for and where per-training plans live.
 * The title reuses the section head's own string so the two cannot drift.
 */
@Composable
internal fun PlanInfoSheetContent(
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ExercisePlanInfoSheet"),
    ) {
        Text(
            modifier = Modifier.padding(bottom = AppDimension.Space.md),
            text = stringResource(R.string.feature_exercise_detail_default_plan),
            style = AppUi.typography.text.section,
            color = AppUi.colors.textPrimary,
        )
        Text(
            modifier = Modifier.padding(bottom = AppDimension.Space.lg),
            text = stringResource(R.string.feature_exercise_edit_plan_info_body),
            style = AppUi.typography.text.body,
            color = AppUi.colors.textSecondary,
        )
        AppButton.Ghost(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ExercisePlanInfoSheetClose"),
            text = stringResource(KitR.string.core_ui_kit_sheet_close),
            onClick = { consume(Action.Click.OnSheetDismiss) },
        )
    }
}

@Preview
@Composable
private fun PlanInfoSheetContentPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PlanInfoSheetContent(consume = {})
    }
}
