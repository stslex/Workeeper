// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.R

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
        headline = stringResource(R.string.feature_all_exercises_empty_headline),
        supportingText = stringResource(R.string.feature_all_exercises_empty_supporting),
        icon = AppIcons.Exercises,
        actionLabel = stringResource(R.string.feature_all_exercises_empty_create),
        onAction = onCreate,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExercisesEmptyStatePreview() {
    AppTheme { ExercisesEmptyState(onCreate = {}) }
}
