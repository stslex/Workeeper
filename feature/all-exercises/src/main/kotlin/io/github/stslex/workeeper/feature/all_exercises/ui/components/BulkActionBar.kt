// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.R

@Composable
internal fun BulkActionBar(
    canDelete: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier1)
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.sm,
            )
            .testTag("AllExercisesBulkActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Secondary(
            modifier = Modifier
                .weight(1f)
                .testTag("AllExercisesBulkArchive"),
            text = stringResource(R.string.feature_all_exercises_bulk_archive),
            onClick = onArchive,
            size = AppButtonSize.MEDIUM,
        )
        AppButton.Destructive(
            modifier = Modifier
                .weight(1f)
                .testTag("AllExercisesBulkDelete"),
            text = stringResource(R.string.feature_all_exercises_bulk_delete),
            onClick = onDelete,
            enabled = canDelete,
            size = AppButtonSize.MEDIUM,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BulkActionBarPreview() {
    AppTheme {
        BulkActionBar(canDelete = true, onArchive = {}, onDelete = {})
    }
}
