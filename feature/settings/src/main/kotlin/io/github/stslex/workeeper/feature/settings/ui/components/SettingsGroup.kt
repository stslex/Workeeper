// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The mockup's `.sgroup` (extraction §5.2) — and the extraction's whole structural point:
 * **there is no container.** A group is air (the parent column's 32dp spacing), an
 * uppercase mono label ([AppLabel], the kit's `.label`), and rows on the page background.
 * No box, no radius, no fill — the 2dp-bordered `SettingsSection` this replaces was the
 * violation `AppSection`'s own KDoc names.
 *
 * Rules are N+1 (§5.3): every [SettingsGroupRow] draws its own top rule; the group closes
 * with one bottom rule here. The extraction flags that this conflicts with `AppSection`'s
 * between-rows-only rule and says report, not resolve — reported with the PR; the mockup's
 * own geometry ships.
 */
@Composable
internal fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppLabel(
            text = label,
            modifier = Modifier
                .padding(horizontal = AppDimension.screenEdge)
                // `.sgroup>.label{margin-bottom:10px}` → 8dp.
                .padding(bottom = AppDimension.Space.sm),
        )
        content()
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
    }
}
