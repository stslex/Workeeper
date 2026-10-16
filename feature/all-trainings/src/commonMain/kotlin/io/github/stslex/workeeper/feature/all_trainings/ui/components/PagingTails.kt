// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.feature.all_trainings.resources.Res
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_paging_error
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_paging_loading
import io.github.stslex.workeeper.feature.all_trainings.resources.feature_all_trainings_paging_retry
import org.jetbrains.compose.resources.stringResource

/**
 * This screen's paging tails — vocabulary and test tags over the kit's drawing. §26: exhausted
 * draws no footer at all.
 */
@Composable
internal fun PagingLoadingFooter(
    modifier: Modifier = Modifier,
) {
    AppPagingLoadingFooter(
        modifier = modifier.testTag("AllTrainingsPagingLoading"),
        label = stringResource(Res.string.feature_all_trainings_paging_loading),
    )
}

/** @param ruled draws the rule separating the footer from the last row. */
@Composable
internal fun PagingErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String = stringResource(Res.string.feature_all_trainings_paging_error),
    ruled: Boolean = true,
) {
    AppPagingErrorFooter(
        modifier = modifier.testTag("AllTrainingsPagingError"),
        retryModifier = Modifier.testTag("AllTrainingsPagingRetry"),
        reason = reason,
        retryLabel = stringResource(Res.string.feature_all_trainings_paging_retry),
        onRetry = onRetry,
        ruled = ruled,
    )
}
