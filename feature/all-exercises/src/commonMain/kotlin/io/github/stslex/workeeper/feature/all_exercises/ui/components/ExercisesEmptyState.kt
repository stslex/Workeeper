// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.all_exercises.resources.Res
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_empty_create
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_empty_headline
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_empty_supporting
import org.jetbrains.compose.resources.stringResource

/**
 * The exercises empty state (`pass2d.html` `#s-empty`, spec §26): glyph, headline, sentence and a
 * single CTA. The glyph is [AppIcons.Exercises], the same vector the nav bar's tab draws.
 */
@Composable
internal fun ExercisesEmptyState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllExercisesEmptyState"),
        headline = stringResource(Res.string.feature_all_exercises_empty_headline),
        supportingText = stringResource(Res.string.feature_all_exercises_empty_supporting),
        icon = AppIcons.Exercises,
        actionLabel = stringResource(Res.string.feature_all_exercises_empty_create),
        onAction = onCreate,
    )
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ExercisesEmptyStateLightPreview() {
    ExercisesEmptyStatePreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ExercisesEmptyStateDarkPreview() {
    ExercisesEmptyStatePreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun ExercisesEmptyStatePreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) { ExercisesEmptyState(onCreate = {}) }
}
