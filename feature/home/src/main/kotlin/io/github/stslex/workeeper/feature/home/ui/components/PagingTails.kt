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
 * Home's paging tails — thin wrappers over the kit's drawing, which is now on its fourth consumer.
 *
 * What stays here is this screen's **vocabulary and its test tags**, the only part that was ever
 * screen-specific. §26 "Paging tails": three states, two drawings — exhausted is no footer at all,
 * so there is deliberately nothing here for it.
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

/**
 * @param reason defaults to the append tail's. The cold-open caller passes its own, because
 *  «дальше» is a lie when nothing loaded at all.
 * @param ruled the drawn rule separates the footer from the last row; at the top of an empty list
 *  there is no row above it to separate from.
 */
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
