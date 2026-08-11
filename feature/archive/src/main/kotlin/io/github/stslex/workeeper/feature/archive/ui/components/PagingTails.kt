// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.feature.archive.R

/**
 * This screen's paging tails — thin wrappers over the kit's drawing, exactly as on both siblings.
 * What stays here is the vocabulary and the test tags. §26 "Paging tails": three states, two
 * drawings; exhausted is no footer at all, so there is deliberately nothing here for it.
 */
@Composable
internal fun PagingLoadingFooter(
    modifier: Modifier = Modifier,
) {
    AppPagingLoadingFooter(
        modifier = modifier.testTag("ArchivePagingLoading"),
        label = stringResource(R.string.feature_archive_paging_loading),
    )
}

@Composable
internal fun PagingErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPagingErrorFooter(
        modifier = modifier.testTag("ArchivePagingError"),
        retryModifier = Modifier.testTag("ArchivePagingRetry"),
        reason = stringResource(R.string.feature_archive_paging_error),
        retryLabel = stringResource(R.string.feature_archive_paging_retry),
        onRetry = onRetry,
        ruled = true,
    )
}
