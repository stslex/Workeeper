// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.all_trainings.resources.Res
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_create
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_headline
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_start_blank
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_empty_supporting
import org.jetbrains.compose.resources.stringResource

/**
 * The trainings empty state (§26: glyph, headline, sentence and both CTAs are contract).
 * [AppIcons.Trainings] is also the nav bar's trainings tab — editing this glyph edits both.
 */
@Composable
internal fun TrainingsEmptyState(
    onCreate: () -> Unit,
    onStartBlank: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllTrainingsEmptyState"),
        headline = stringResource(Res.string.feature_all_trainings_empty_headline),
        supportingText = stringResource(Res.string.feature_all_trainings_empty_supporting),
        icon = AppIcons.Trainings,
        actionLabel = stringResource(Res.string.feature_all_trainings_empty_create),
        onAction = onCreate,
        secondaryActionLabel = onStartBlank?.let {
            stringResource(Res.string.feature_all_trainings_empty_start_blank)
        },
        onSecondaryAction = onStartBlank,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun TrainingsEmptyStateLightPreview() {
    TrainingsEmptyStatePreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun TrainingsEmptyStateDarkPreview() {
    TrainingsEmptyStatePreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun TrainingsEmptyStatePreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) { TrainingsEmptyState(onCreate = {}, onStartBlank = {}) }
}
