// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.R

/**
 * The trainings empty state — `pass2d.html` `#s-empty`'s first `.empty`.
 *
 * §26 "Empty state": the glyph, the headline, the sentence and **both** CTAs are contract, not
 * suggestion. It shipped with a filled Material dumbbell and no actions at all; every one of those
 * is now the drawn mark, the drawn strings and the drawn pair.
 *
 * The glyph is [AppIcons.Trainings], which is the same path the bottom bar takes for its trainings
 * tab — one mark, two consumers, so it cannot drift between them.
 *
 * Placement stays delegated: §13 gives the pattern to the kit and the placement to the screen.
 */
@Composable
internal fun TrainingsEmptyState(
    onCreate: () -> Unit,
    onStartBlank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllTrainingsEmptyState"),
        headline = stringResource(R.string.feature_all_trainings_empty_headline),
        supportingText = stringResource(R.string.feature_all_trainings_empty_supporting),
        icon = AppIcons.Trainings,
        actionLabel = stringResource(R.string.feature_all_trainings_empty_create),
        onAction = onCreate,
        secondaryActionLabel = stringResource(R.string.feature_all_trainings_empty_start_blank),
        onSecondaryAction = onStartBlank,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TrainingsEmptyStatePreview() {
    AppTheme { TrainingsEmptyState(onCreate = {}, onStartBlank = {}) }
}
