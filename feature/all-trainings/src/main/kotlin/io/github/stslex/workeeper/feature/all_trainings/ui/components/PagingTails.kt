// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.feature.all_trainings.R

/**
 * This screen's paging tails — thin wrappers over the kit's drawing.
 *
 * The treatment moved to `core:ui:kit` when `feature/archive` became its third consumer; what stays
 * here is this screen's **vocabulary and its test tags**, which is the only part that was ever
 * screen-specific. §26 "Paging tails": three states, two drawings — exhausted is no footer at all,
 * so there is deliberately nothing here for it.
 */
@Composable
internal fun PagingLoadingFooter(
    modifier: Modifier = Modifier,
) {
    AppPagingLoadingFooter(
        modifier = modifier.testTag("AllTrainingsPagingLoading"),
        label = stringResource(R.string.feature_all_trainings_paging_loading),
    )
}

/**
 * @param reason defaults to the append tail's. The cold-open caller passes its own, because
 *  «дальше» is a lie when nothing loaded at all.
 * @param ruled the drawn rule separates the footer from the last row; at the top of an empty list
 *  there is no row above it.
 */
@Composable
internal fun PagingErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String = stringResource(R.string.feature_all_trainings_paging_error),
    ruled: Boolean = true,
) {
    AppPagingErrorFooter(
        modifier = modifier.testTag("AllTrainingsPagingError"),
        retryModifier = Modifier.testTag("AllTrainingsPagingRetry"),
        reason = reason,
        retryLabel = stringResource(R.string.feature_all_trainings_paging_retry),
        onRetry = onRetry,
        ruled = ruled,
    )
}
