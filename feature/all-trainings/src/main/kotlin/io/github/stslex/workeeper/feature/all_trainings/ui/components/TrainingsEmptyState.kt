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
        headline = stringResource(R.string.feature_all_trainings_empty_headline),
        supportingText = stringResource(R.string.feature_all_trainings_empty_supporting),
        icon = AppIcons.Trainings,
        actionLabel = stringResource(R.string.feature_all_trainings_empty_create),
        onAction = onCreate,
        secondaryActionLabel = onStartBlank?.let {
            stringResource(R.string.feature_all_trainings_empty_start_blank)
        },
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
