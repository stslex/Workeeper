// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.feature.home.R

/**
 * Home's paging tails — vocabulary and test tags over the kit's drawing. §26: exhausted draws no
 * footer at all.
 */
@Composable
internal fun PagingLoadingFooter(
    modifier: Modifier = Modifier,
) {
    AppPagingLoadingFooter(
        modifier = modifier.testTag("HomePagingLoading"),
        label = stringResource(R.string.feature_home_paging_loading),
    )
}

/** @param ruled draws the rule separating the footer from the last row. */
@Composable
internal fun PagingErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String = stringResource(R.string.feature_home_paging_error),
    ruled: Boolean = true,
) {
    AppPagingErrorFooter(
        modifier = modifier.testTag("HomePagingError"),
        retryModifier = Modifier.testTag("HomePagingRetry"),
        reason = reason,
        retryLabel = stringResource(R.string.feature_home_paging_retry),
        onRetry = onRetry,
        ruled = ruled,
    )
}
