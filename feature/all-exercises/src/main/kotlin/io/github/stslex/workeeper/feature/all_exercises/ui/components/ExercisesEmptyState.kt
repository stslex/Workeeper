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
 * The exercises empty state — `pass2d.html` `#s-empty`'s third `.empty`.
 *
 * §26 "Empty state": the glyph, the headline, the sentence and the CTAs are contract. It shipped
 * with a filled Material dumbbell, "Tap + to create your first exercise" and **no action at all**;
 * every one of those is now the drawn mark, the drawn sentence and the drawn button.
 *
 * **One CTA, not two.** The trainings empty draws a pair because it has two genuinely different
 * entry points; this one draws «Добавить упражнение» and nothing else. `AppEmptyState` already
 * renders zero, one or two actions from its call site, so the difference is entirely in the call —
 * which is also the answer to the open "is `.empty` a component with variants or a pattern
 * instantiated per screen" question: two screens now use it at different arities and it did not
 * need to grow a variant axis.
 *
 * The sentence changed for the reason `AppEmptyState`'s own KDoc gives: the old one narrated an
 * affordance the user has to go find, the drawn one says what the thing is *for* — "add the first
 * one, after that you can put it into any training".
 *
 * The glyph is [AppIcons.Exercises]. §26 "Bottom navigation" takes the nav bar's icons from "the
 * drawn empty-state glyphs verbatim", so this path is *owed* a second consumer — the bar still
 * draws `@DrawableRes` XML (`BottomBarItem`) and this is the path's only call site. The coupling is
 * a decision already taken, not a fact about the tree; [AppIcons.Trainings] carries the same
 * pending status and the same warning.
 *
 * Placement stays delegated: §13 gives the pattern to the kit and the placement to the screen.
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
