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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * A section's header: one label on the left, an optional second on the right.
 *
 * The right-hand label is the part that is easy to mistake for decoration. In the mockups it is
 * never a title — it is always a *count* or a *mode*, and it is the only place either is stated:
 * `История` / `4 сессии` and `Записано` / `можно править` (`pass2d.html:283,301`). Both labels are
 * the same class in the mockup (`.label`), so they are the same style here; the right one is not a
 * quieter variant of the left, it is a peer.
 *
 * Baseline-aligned rather than centre-aligned, so the two labels sit on one optical line even
 * though the right-hand one is often shorter and numeric.
 *
 * ## Why the labels are uppercased here rather than by the caller
 *
 * The mockup's `.label` carries `text-transform:uppercase` (`pass2d.html:38`), i.e. the casing is
 * a property of the *style*, not of the string. Uppercasing at the call site would push a display
 * concern into every strings.xml, and would be wrong for Russian the moment a caller forgot.
 * [String.uppercase] is locale-aware and correct for the Cyrillic these labels are mostly written
 * in (`История` -> `ИСТОРИЯ`).
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
 * The label rung, shared by both sides so they cannot drift apart.
 *
 * `mono.caption` is the smallest step of the six-step scale in the mono family — the mockup draws
 * these in `--ff-mono` at 11px (`pass2d.html:38`), which is exactly this rung.
 *
 * The colour is [io.github.stslex.workeeper.core.ui.kit.theme.AppColors.textTertiary], **not** the
 * mockup's `--dim`. `--dim` is a fifth text tier this palette does not have, and adopting it as
 * drawn would ship a WCAG 1.4.3 failure: dark `--dim` `#6B7078` on `base` `#0B0D0F` measures
 * **3.91:1** against the 4.5:1 that 11sp owes. That is the same trap the palette already
 * documents for light `meta` — the mockup was drawn, not measured. `textTertiary` is the existing
 * tier that carries captions, it is already declared against every surface in the contrast
 * contract, and it passes. Adding the fifth tier is a palette decision with a measured number
 * attached now; it is not this step's to take.
 */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = text.uppercase(),
        style = AppUi.typography.mono.caption,
        color = AppUi.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
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
