// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.R

/**
 * The two empty states a user *arrives at*, and the one the list shows while it does not yet know.
 *
 * §26 "List states reached by an action" rules the discriminator these share: **a glyph tile means
 * the screen is empty by itself; no tile means you got here by an action you can undo.** The
 * first-run empty keeps its tile; neither of these has one, and that difference is legible before
 * a word is read.
 */

/**
 * A tag filter matched nothing.
 *
 * **No supporting sentence, deliberately.** The pattern's shape is glyph / title / one sentence,
 * and this block drops two of the three. There is no honest instruction to put there: the filter is
 * **OR** (`tag_uuid IN (:tags)`, in both DAOs), so the result set only shrinks as chips come off
 * and "remove a tag" cannot recover — the sole true chip-level advice is "add another tag", which is
 * a non-sequitur to someone whose list just vanished. A consequence clause would restate the button
 * in longer words.
 *
 * The action **clears** rather than creates. Creating under a filter the user just used answers a
 * question they did not ask, and leaves the filter in place so the new thing vanishes on arrival.
 * One tap rather than N: the band scrolls horizontally with no scrollbar, so a lit chip can be
 * off-screen.
 */
@Composable
internal fun FilteredEmptyState(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllExercisesFilteredEmpty"),
        headline = stringResource(R.string.feature_all_exercises_filtered_empty_headline),
        icon = null,
        actionLabel = stringResource(R.string.feature_all_exercises_filtered_empty_clear),
        onAction = onClearFilter,
    )
}

/**
 * Selection is running and the list emptied under it.
 *
 * The body owes **no count and no actions**: the top bar still prints the count and still carries
 * the exit and the archive, and repeating them is verbatim the argument that rejected the
 * count-bearing FAB. It owes exactly **one line**, because a blank body in this state is
 * byte-identical to the cold-open void — and would say "still loading" to the one user who most
 * needs to know it is not.
 *
 * The sentence is checked against the code rather than written to reassure: leaving the mode
 * discards the selection whole (`selectedUuids` lives inside `SelectionMode.On`), so "until you
 * leave the mode" is the entire promise and not a hedge on it.
 *
 * [onClearFilter] is **null when no filter is active** — the emptiness then came from another
 * screen archiving the last row, and there is nothing here to undo. `AppEmptyState` renders a
 * button only when label *and* handler are non-null, which its own KDoc records as load-bearing.
 */
@Composable
internal fun SelectionEmptyState(
    onClearFilter: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllExercisesSelectionEmpty"),
        headline = stringResource(R.string.feature_all_exercises_selection_empty_headline),
        supportingText = stringResource(R.string.feature_all_exercises_selection_empty_supporting),
        icon = null,
        actionLabel = onClearFilter?.let { stringResource(R.string.feature_all_exercises_filtered_empty_clear) },
        onAction = onClearFilter,
    )
}

/**
 * The cold open — refresh has not settled and there are no rows yet.
 *
 * **Not an empty state.** The list is not empty here, it is *unknown*, and B22 exists precisely
 * because the old predicate could not tell those apart. This is [PagingLoadingFooter] — the same
 * footer, unmoved and unrestyled — placed where **row 1 will land** rather than centred in the
 * void, so the layout settles once when the page arrives instead of twice. Zero new strings.
 */
@Composable
internal fun ColdOpenLoading(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimension.Space.sm)
            .testTag("AllExercisesColdOpen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        PagingLoadingFooter()
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ArrivedEmptyStatesPreview() {
    AppTheme {
        Column {
            FilteredEmptyState(onClearFilter = {})
            SelectionEmptyState(onClearFilter = {})
            SelectionEmptyState(onClearFilter = null)
            ColdOpenLoading()
        }
    }
}
