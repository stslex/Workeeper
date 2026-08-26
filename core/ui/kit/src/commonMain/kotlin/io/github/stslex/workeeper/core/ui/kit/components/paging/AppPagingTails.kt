// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.paging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

/**
 * The paging tails: a loading footer and an error footer with a retry; exhausted draws no footer
 * at all. Strings stay with the caller so one drawing serves three vocabularies.
 */
@Composable
fun AppPagingLoadingFooter(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimension.Space.xl),
        horizontalArrangement = Arrangement.spacedBy(
            space = AppDimension.Space.sm,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLoadingIndicator(modifier = Modifier.size(AppDimension.iconSm))
        Text(
            text = label,
            style = AppUi.typography.mono.caption,
            color = AppUi.colors.textTertiary,
        )
    }
}

/**
 * The reason truncates rather than wrapping. GUARD: pass `ruled = false` at the top of an empty
 * list — the rule separates the footer from the row above, and there is none there.
 */
@Composable
fun AppPagingErrorFooter(
    reason: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    ruled: Boolean = true,
    retryModifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (ruled) {
            HorizontalDivider(
                thickness = AppDimension.borderHairline,
                color = AppUi.colors.borderSubtle,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDimension.screenEdge,
                    vertical = AppDimension.Space.md,
                ),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = reason,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppButton.Tertiary(
                modifier = retryModifier,
                text = retryLabel,
                size = AppButtonSize.MEDIUM,
                onClick = onRetry,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPagingTailsPreview() {
    AppTheme {
        Column {
            AppPagingLoadingFooter(label = "Loading")
            AppPagingErrorFooter(
                reason = "Couldn’t load more",
                retryLabel = "Retry",
                onRetry = {},
            )
        }
    }
}
