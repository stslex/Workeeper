// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedControl
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import kotlinx.collections.immutable.toImmutableList

/**
 * WEIGHTED / WEIGHTLESS, beside the rows whose shape it decides — the enum ↔ index adapter onto
 * [AppSegmentedControl], which owns the appearance (`v3-editors.md` ED5).
 */
@Composable
internal fun TypeToggle(
    selected: ExerciseTypeUiModel,
    onSelect: (ExerciseTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ExerciseTypeUiModel.entries
    AppSegmentedControl(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PlanEditorTypeToggle"),
        items = options.map { stringResource(it.labelRes) }.toImmutableList(),
        selected = options.indexOf(selected),
        onSelectedChange = { index -> onSelect(options[index]) },
        itemModifier = { index -> Modifier.testTag("PlanEditorTypeOption_${options[index].name}") },
    )
}

@Preview
@Composable
private fun TypeToggleWeightedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        TypeToggle(
            selected = ExerciseTypeUiModel.WEIGHTED,
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun TypeToggleWeightlessDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        TypeToggle(
            selected = ExerciseTypeUiModel.WEIGHTLESS,
            onSelect = {},
        )
    }
}
