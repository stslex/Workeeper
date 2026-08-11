// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * A section: air, a header, and rows separated by hairlines.
 *
 * This is what replaces the card. A card said "these things belong together" with a filled,
 * rounded container; a section says it with **space and a label**, and nothing else. The rule that
 * follows from that, and the one most likely to be eroded later:
 *
 * > Section separation is carried by the gutter above and the header label. It is **not** carried
 * > by a line. There is no rule above the first row and none below the last.
 *
 * The hairlines that *do* exist are intra-section only — they separate sibling rows, which is a
 * different job. Because they carry no information a user could lose (the section boundary is
 * already stated twice, by air and by label), they are decoration under WCAG 1.4.11 and take no
 * contrast threshold. That is why they are drawn in `borderSubtle`, the palette's decorative
 * hairline, and not in a control-outline colour.
 *
 * ## Why rows arrive through a scope instead of a `content:` slot
 *
 * "Hairlines between rows" is a fact about *adjacency*. A plain `@Composable ColumnScope.() -> Unit`
 * slot cannot express it: the section would have no idea where one row ends and the next begins,
 * so the divider would have to be drawn by each row — and a row drawing its own divider is exactly
 * how a line appears under the last row of every section.
 *
 * Declaring rows through [AppSectionScope.row] makes adjacency structural. The section knows the
 * row count before it composes anything, so "between" is arithmetic rather than convention, and a
 * caller cannot get it wrong.
 *
 * For a lazily-scrolled list the rows are not known up front and this container does not apply —
 * use [AppSectionHeader] and [AppSectionDivider] directly inside the `LazyColumn`.
 */
@Composable
fun AppSection(
    modifier: Modifier = Modifier,
    label: String? = null,
    trailingLabel: String? = null,
    content: AppSectionScope.() -> Unit,
) {
    val rows = AppSectionScope().apply(content).rows
    Column(modifier = modifier.fillMaxWidth()) {
        // The air above. The mockup writes 30px on `.sgroup` and 32px on `.section-head`
        // (`pass2d.html:155,63`) — the same gap, drawn twice, two pixels apart. Rounding both onto
        // the ladder resolves the inconsistency at Space.xxl (32.dp) rather than preserving it.
        Spacer(modifier = Modifier.height(AppDimension.Space.xxl))
        label?.let { text ->
            AppSectionHeader(label = text, trailingLabel = trailingLabel)
            Spacer(modifier = Modifier.height(AppDimension.Space.md))
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) AppSectionDivider()
            row()
        }
    }
}

/**
 * Collects the rows of one [AppSection] so the container can reason about adjacency.
 *
 * The lambdas are stored, not invoked, during collection — they are composed later, in order, by
 * [AppSection]. This is the same shape as `LazyListScope.item`, minus the laziness.
 */
class AppSectionScope internal constructor() {

    internal val rows: MutableList<@Composable () -> Unit> = mutableListOf()

    /** Declares one row. Rows appear in declaration order, with a hairline between neighbours. */
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * The intra-section hairline.
 *
 * Inset by [AppDimension.screenEdge] on the left so it starts where the row's text starts rather
 * than at the screen edge, which is what stops a stack of rows reading as a table.
 *
 * Drawn as a [background] on a fixed-height box rather than with `HorizontalDivider`, so its
 * thickness is this project's [AppDimension.borderHairline] (0.5.dp) instead of Material's 1.dp.
 * At the golden device's 2.75 px/dp that is a 1-pixel rule — see the hairline canary, which exists
 * to keep exactly this from drifting.
 */
@Composable
fun AppSectionDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppDimension.screenEdge)
            .height(AppDimension.borderHairline)
            .background(AppUi.colors.borderSubtle),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSectionPreview() {
    AppTheme {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            AppSection(label = "Appearance") {
                row { AppSectionRow(title = "Theme", supporting = "System", onClick = {}) }
                row { AppSectionRow(title = "Units", supporting = "Kilograms", onClick = {}) }
            }
            AppSection(label = "History", trailingLabel = "4 sessions") {
                row { AppSectionRow(title = "Upper body", supporting = "23 July - 48 min") }
                row { AppSectionRow(title = "Legs", supporting = "21 July - 55 min") }
                row { AppSectionRow(title = "Pull", supporting = "19 July - 41 min") }
            }
        }
    }
}
