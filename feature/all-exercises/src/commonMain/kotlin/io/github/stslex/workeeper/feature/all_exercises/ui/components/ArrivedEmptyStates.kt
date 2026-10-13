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
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.all_exercises.resources.Res
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_filtered_empty_clear
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_filtered_empty_headline
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_refresh_error
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_selection_empty_headline
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_selection_empty_supporting
import org.jetbrains.compose.resources.stringResource

/**
 * A tag filter matched nothing. No supporting sentence, and the action clears rather than creates:
 * the filter is OR, so taking one chip off cannot recover the list.
 */
@Composable
internal fun FilteredEmptyState(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllExercisesFilteredEmpty"),
        headline = stringResource(Res.string.feature_all_exercises_filtered_empty_headline),
        icon = null,
        actionLabel = stringResource(Res.string.feature_all_exercises_filtered_empty_clear),
        onAction = onClearFilter,
    )
}

/**
 * Selection is running and the list emptied under it; the count and the actions stay in the top
 * bar. [onClearFilter] is null when no filter is active, which drops the button entirely.
 */
@Composable
internal fun SelectionEmptyState(
    onClearFilter: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllExercisesSelectionEmpty"),
        headline = stringResource(Res.string.feature_all_exercises_selection_empty_headline),
        supportingText = stringResource(Res.string.feature_all_exercises_selection_empty_supporting),
        icon = null,
        actionLabel = onClearFilter?.let { stringResource(Res.string.feature_all_exercises_filtered_empty_clear) },
        onAction = onClearFilter,
    )
}

/**
 * The cold open: refresh has not settled and there are no rows yet. Not an empty state — it is
 * [PagingLoadingFooter] placed where row 1 will land, so the layout settles once.
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

/**
 * The first page failed: the drawn `.perr` moved to where row 1 would be, unruled because no row
 * sits above it, with its own reason string since nothing loaded to have failed further.
 */
@Composable
internal fun ColdOpenError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimension.Space.sm)
            .testTag("AllExercisesColdOpenError"),
    ) {
        PagingErrorFooter(
            onRetry = onRetry,
            reason = stringResource(Res.string.feature_all_exercises_refresh_error),
            ruled = false,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ArrivedEmptyStatesLightPreview() {
    ArrivedEmptyStatesPreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ArrivedEmptyStatesDarkPreview() {
    ArrivedEmptyStatesPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun ArrivedEmptyStatesPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
        Column {
            FilteredEmptyState(onClearFilter = {})
            SelectionEmptyState(onClearFilter = {})
            SelectionEmptyState(onClearFilter = null)
            ColdOpenLoading()
            ColdOpenError(onRetry = {})
        }
    }
}
