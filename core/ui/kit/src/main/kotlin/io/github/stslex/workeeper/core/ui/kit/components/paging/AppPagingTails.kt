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

/**
 * The paging tails — `pass2d.html` `#s-list`'s loading and error frames.
 *
 * §26 "Paging tails": three states, **two drawings**. Loading is a footer spinner; error is the
 * reason plus a retry, because a silently truncated list is indistinguishable from a finished one;
 * and **exhausted is no footer at all** — "end of list" states only what is already visible, so the
 * absence is the drawing and there is deliberately nothing here for it.
 *
 * ## Why these are in the kit rather than copied a third time
 *
 * They were duplicated in `all-trainings` and `all-exercises`, which was defensible at two: the
 * copies were identical, and the alternative was a shared component before its shape was known.
 * `feature/archive` is the third consumer — the navnote names all three ("Пагинация уже есть в
 * тренировках, упражнениях и архиве") — and a third copy is where "not yet" stops being a judgement
 * and starts being drift. Three consumers of one drawn treatment is the forcing case.
 *
 * Strings stay with the caller. They are per-feature resources today, and hoisting them would make
 * this component own copy for screens it knows nothing about; taking them as parameters is what
 * lets one drawing serve three vocabularies.
 *
 * ## One treatment, two positions
 *
 * [AppPagingErrorFooter] is also `#s-empty`'s refresh-error block: the same `.perr` moved to where
 * row 1 would be. Only [reason] and [ruled] vary, and both follow from the position — see their
 * docs.
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
 * The reason truncates rather than wrapping: it inherits the drawn `.frame .meta` rule, which is
 * one line with an ellipsis at the tail.
 *
 * @param reason why the load failed. The append tail's «дальше» is a lie at the top of an empty
 *  list — there is no *further* to have failed — so the cold-open caller passes its own.
 * @param ruled the drawn `border-top` separates the footer from the **last row**. At the top of an
 *  empty list there is no row above it to separate from, so the drawing omits it there.
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
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
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
