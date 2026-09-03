// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingErrorFooter
import io.github.stslex.workeeper.core.ui.kit.components.paging.AppPagingLoadingFooter
import io.github.stslex.workeeper.feature.all_exercises.R

/**
 * This screen's paging tails — thin wrappers adding its vocabulary and test tags over the kit's
 * drawing. Exhausted is no footer at all, so there is deliberately nothing here for it.
 */
@Composable
internal fun PagingLoadingFooter(
    modifier: Modifier = Modifier,
) {
    AppPagingLoadingFooter(
        modifier = modifier.testTag("AllExercisesPagingLoading"),
        label = stringResource(R.string.feature_all_exercises_paging_loading),
    )
}

/**
 * @param reason defaults to the append tail's; the cold-open caller passes its own.
 * @param ruled false at the top of an empty list, where no row sits above the footer.
 */
@Composable
internal fun PagingErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String = stringResource(R.string.feature_all_exercises_paging_error),
    ruled: Boolean = true,
) {
    AppPagingErrorFooter(
        modifier = modifier.testTag("AllExercisesPagingError"),
        retryModifier = Modifier.testTag("AllExercisesPagingRetry"),
        reason = reason,
        retryLabel = stringResource(R.string.feature_all_exercises_paging_retry),
        onRetry = onRetry,
        ruled = ruled,
    )
}
