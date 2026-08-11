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
 * WEIGHTED / WEIGHTLESS, as one instance beside the rows whose shape it decides.
 *
 * **It lives here, with [PlanEditorBody], because the type and the sets are one edit.** The toggle
 * decides whether a row has a weight column at all, so a host that draws the rows and cannot reach
 * the toggle has to send the user somewhere else to change what those rows mean — which is the
 * split this placement closes. [PlanEditorBody] renders it; no host renders it directly, so there
 * is one instance and not a copy per host.
 *
 * **The control is [AppSegmentedControl] and this is the adapter onto it** (`v3-editors.md` ED5).
 * One of two things chosen from a track is what that component already is, so what is left here is
 * the enum ↔ index mapping and nothing about appearance: no fill, no outline, no colour. The
 * monochrome `.tabs` grammar the ruling names — track `surfaceTier1`, selected on `surfaceTier2` +
 * `slabtop`, labels `--max` / `--meta` — is the kit component's, so a second host of that grammar
 * cannot drift from this one.
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
