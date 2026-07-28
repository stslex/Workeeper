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
 * The colour is [io.github.stslex.workeeper.core.ui.kit.theme.AppColors.textDim] — the mockup's
 * `--dim` role, which the palette now names explicitly and **aliases onto `meta`**. This label is
 * the reason the alias exists: `--dim` cannot ship as drawn in either theme (dark `#6B7078`
 * bottoms out at 2.87:1 on `raise`, light `#98A0A9` at 2.05:1, against the 4.5:1 an 11sp label
 * owes), and the corrected value lands on `meta`. Reading [textDim] rather than `textTertiary`
 * keeps the *role* visible at the call site: if the fourth tier is ever reinstated as a
 * restricted large-type role, this site is one of the ones that must not follow it.
 */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppLabel(text = text, modifier = modifier)
}

/**
 * The mockups' `.label` — the uppercase mono caption every screen uses for section heads,
 * rail metadata and header eyebrows (extraction §0.8, kit-candidate rank 2; previously
 * private in this file). Casing is design (`text-transform:uppercase`), so it is applied
 * here, locale-aware; the declared `.14em` tracking stays unimplemented by design (B4 —
 * positive mono tracking is per-component, and this component follows the caption rung).
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
