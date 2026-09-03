// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

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
import io.github.stslex.workeeper.feature.all_trainings.R

/** A tag filter matched nothing. No tile; the action clears the filter rather than creating. */
@Composable
internal fun FilteredEmptyState(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllTrainingsFilteredEmpty"),
        headline = stringResource(R.string.feature_all_trainings_filtered_empty_headline),
        icon = null,
        actionLabel = stringResource(R.string.feature_all_trainings_filtered_empty_clear),
        onAction = onClearFilter,
    )
}

/**
 * Selection is running and the list emptied under it. [onClearFilter] is null when no filter is
 * active, and `AppEmptyState` then renders no button at all.
 */
@Composable
internal fun SelectionEmptyState(
    onClearFilter: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllTrainingsSelectionEmpty"),
        headline = stringResource(R.string.feature_all_trainings_selection_empty_headline),
        supportingText = stringResource(R.string.feature_all_trainings_selection_empty_supporting),
        icon = null,
        actionLabel = onClearFilter?.let { stringResource(R.string.feature_all_trainings_filtered_empty_clear) },
        onAction = onClearFilter,
    )
}

/**
 * The cold open: refresh unsettled, no rows yet. [PagingLoadingFooter] placed where row 1 will
 * land rather than centred, so the layout settles once when the page arrives.
 */
@Composable
internal fun ColdOpenLoading(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimension.Space.sm)
            .testTag("AllTrainingsColdOpen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        PagingLoadingFooter()
    }
}

/**
 * The first page failed: the paging error footer moved to where row 1 would be, unruled because
 * no row sits above it, and with its own reason string.
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
            .testTag("AllTrainingsColdOpenError"),
    ) {
        PagingErrorFooter(
            onRetry = onRetry,
            reason = stringResource(R.string.feature_all_trainings_refresh_error),
            ruled = false,
        )
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
            ColdOpenError(onRetry = {})
        }
    }
}
