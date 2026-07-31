// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_trainings.R

/**
 * The paging tails — `pass2d.html` `#s-list`'s loading and error frames.
 *
 * §26 "Paging tails": three states, **two drawings**. Loading is a footer spinner; error is the
 * reason plus a retry, because a silently truncated list is indistinguishable from a finished one;
 * and **exhausted is no footer at all** — "end of list" states only what is already visible, so the
 * absence is the drawing and there is deliberately nothing here for it.
 *
 * They live in their own file rather than inside the screen's `LazyListScope` block for the reason
 * the confirm dialog's content was split out: a surface with a drawn treatment and no way to
 * photograph it has no visual gate. As composables they golden like anything else.
 */
@Composable
internal fun PagingLoadingFooter(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimension.Space.xl)
            .testTag("AllTrainingsPagingLoading"),
        horizontalArrangement = Arrangement.spacedBy(
            space = AppDimension.Space.sm,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLoadingIndicator(modifier = Modifier.size(AppDimension.iconSm))
        Text(
            text = stringResource(R.string.feature_all_trainings_paging_loading),
            style = AppUi.typography.mono.caption,
            color = AppUi.colors.textTertiary,
        )
    }
}

/**
 * The reason truncates rather than wrapping: it inherits the drawn `.frame .meta` rule, which is
 * one line with an ellipsis at the tail.
 */
@Composable
internal fun PagingErrorFooter(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.md,
            )
            .testTag("AllTrainingsPagingError"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.feature_all_trainings_paging_error),
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppButton.Tertiary(
            modifier = Modifier.testTag("AllTrainingsPagingRetry"),
            text = stringResource(R.string.feature_all_trainings_paging_retry),
            size = AppButtonSize.MEDIUM,
            onClick = onRetry,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PagingTailsPreview() {
    AppTheme {
        androidx.compose.foundation.layout.Column {
            PagingLoadingFooter()
            PagingErrorFooter(onRetry = {})
        }
    }
}
