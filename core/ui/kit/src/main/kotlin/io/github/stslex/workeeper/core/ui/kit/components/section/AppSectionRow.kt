// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.section

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * One row inside an [AppSection].
 *
 * ## Composition, which is what produces the height
 *
 * Left to right: an optional [leading] slot, then the text column ([title] over an optional
 * [supporting]), then an optional [trailing] slot. The text column takes the remaining width; both
 * slots are intrinsically sized.
 *
 * Top to bottom, the text column is one or two lines of `text.body` over one line of `mono.meta`.
 * That, plus symmetric [AppDimension.Space.md] padding, is where
 * [AppDimension.rowHeight] comes from — see its KDoc for the arithmetic. The height is a
 * **minimum**, so the row grows rather than clips when a title wraps to three lines or the user
 * raises the font scale.
 *
 * A row with no [supporting] text has nothing to be 88.dp tall for, so it settles at
 * [AppDimension.heightXl] (64.dp) instead — the mockup draws exactly this split, `.row` at 88 for
 * the two-line list rows and `.srow` at 64 for the single-line settings rows
 * (`pass2d.html:70,157`). Both clear the 48.dp touch-target minimum.
 *
 * ## What this row does not draw
 *
 * It draws no separator. Hairlines belong to [AppSection], which is the only place that knows
 * which rows have a neighbour — a row that drew its own divider would put one under the last row
 * of every section, which is the line the v3 structure deliberately does not have.
 */
@Composable
fun AppSectionRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val minHeight = if (supporting == null) AppDimension.heightXl else AppDimension.rowHeight
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = minHeight)
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.md,
            ),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            Text(
                text = title,
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supporting?.let { text ->
                Text(
                    text = text,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSectionRowPreview() {
    AppTheme {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            AppSectionRow(
                title = "Bench press",
                supporting = "5 x 80 kg - 3 days ago",
                onClick = {},
            )
            AppSectionRow(title = "Units", onClick = {})
            AppSectionRow(
                title = "A title long enough to wrap onto the second line the row is built for",
                supporting = "12 sessions",
            )
        }
    }
}
