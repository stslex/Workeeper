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
 * The glyph is [AppIcons.Trainings]. §26 "Bottom navigation" takes the nav bar's icons from "the
 * drawn empty-state glyphs verbatim", and **the second consumer has arrived**: the rebuilt
 * `AppNavBar` draws this same `ImageVector` as its trainings tab, so the coupling is now a fact
 * about the tree rather than a decision on record — editing this glyph edits the nav bar. The
 * `@DrawableRes` XML the bar used to draw (`ic_bottom_app_bar_*`) is deleted.
 *
 * Placement stays delegated: §13 gives the pattern to the kit and the placement to the screen.
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
