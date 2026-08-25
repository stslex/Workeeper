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
 * A section: air, a header, and rows separated by hairlines. GUARD: no rule above the first row
 * and none below the last. Lazy lists use [AppSectionHeader] and [AppSectionDivider] directly.
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
        // The air above; the mockup's 30px/32px pair rounds onto Space.xxl.
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
 * Collects the rows of one [AppSection] so the container can reason about adjacency; the
 * lambdas are stored, not invoked, and composed later in order.
 */
class AppSectionScope internal constructor() {

    internal val rows: MutableList<@Composable () -> Unit> = mutableListOf()

    /** Declares one row. Rows appear in declaration order, with a hairline between neighbours. */
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * The intra-section hairline, inset on the left so it starts where the row's text starts.
 * A [background] box rather than `HorizontalDivider`, to keep [AppDimension.borderHairline].
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
