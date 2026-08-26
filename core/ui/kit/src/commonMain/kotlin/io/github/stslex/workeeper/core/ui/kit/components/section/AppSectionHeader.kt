// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

/**
 * A section's header: one label on the left, an optional count-or-mode label on the right —
 * a peer in the same style, not a quieter variant.
 */
@Composable
fun AppSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(
            text = label,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
        trailingLabel?.let { trailing ->
            SectionLabel(
                text = trailing,
                modifier = Modifier.padding(start = AppDimension.Space.sm),
            )
        }
    }
}

/**
 * The label rung, shared by both sides so they cannot drift apart; it reads `textDim` rather
 * than `textTertiary` to keep the role visible. See documentation/design-system.md.
 */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppLabel(text = text, modifier = modifier)
}

/**
 * The mockups' `.label` — the uppercase mono caption used for section heads, rail metadata
 * and header eyebrows. Casing is design, so it is applied here, locale-aware.
 */
@Composable
fun AppLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppUi.colors.textDim,
) {
    Text(
        modifier = modifier,
        text = text.uppercase(),
        style = AppUi.typography.mono.caption,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSectionHeaderPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(vertical = AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xl),
        ) {
            AppSectionHeader(label = "Default plan")
            AppSectionHeader(label = "History", trailingLabel = "4 sessions")
            AppSectionHeader(label = "Recorded", trailingLabel = "editable")
        }
    }
}
