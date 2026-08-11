// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The one surface in the app that reads as **"this is what is being done now"**.
 *
 * ## The invariant
 *
 * Exactly one element on screen may say it. Not one per screen, not one per list — one. In v3 that
 * element is the active exercise in the live workout; everything else in the app sits flat.
 *
 * This is the invariant the whole structure rests on, and it is the one most likely to die quietly:
 * somebody adds a screen, gives a container the same treatment, and the principle is gone with
 * nobody noticing. A comment cannot hold that. `ActiveSurfaceSingleReaderRule` in `:lint-rules`
 * does: it permits this composable exactly one call site, and adding a second is a detekt failure
 * rather than a review that might catch it. Widening the permitted set is a deliberate edit to the
 * rule, which is the point.
 *
 * ## Why the invariant lives on a composable and not on a colour
 *
 * Because raisedness is not a colour. The v3 mockups express it as a surface **plus an edge
 * treatment** (`.card.active` is `--slab` + `--slabtop`), and this app expressed it for a while as
 * an animated accent **border** on the ordinary card tier. A rule that counted reads of one palette
 * slot would pass three raised cards that all used surface-plus-shadow, and fail an innocent chip
 * that happened to share a hex. Counting call sites of the treatment is the check that matches the
 * invariant — and it is the reason this rule survived the mechanism actually being built.
 *
 * Note also that the token named `raise` is a red herring here: in both mockups `--raise` is a
 * *utility* fill — the empty progress track, the selected tag, a hover state — and never a panel.
 * `surfaceTier4` and `accentTintedBackground` carry that hex in exactly that utility role and are
 * deliberately **not** under this rule.
 *
 * ## What expresses "active" — resolved
 *
 * `--slab` plus `--slabtop`: `.card.active{background:var(--slab);box-shadow:var(--slabtop)}`
 * (`session-v3f.html:66`). Nothing else. **The mockup draws no border in any state**, and the
 * animated `accent` border this app used instead was a substitution, not the design.
 *
 * This composable is therefore a thin wrapper over [liftedSurface], which is the mechanism and has
 * four consumers. The wrapper is what carries the *semantics*, and the semantics are what the rule
 * guards: a lifted tab indicator is not "what is being done now", and the rule would be wrong to
 * stop it. Lift is available to anything; being *the* active surface is not.
 *
 * [active] is a parameter rather than a condition at the call site, so the one permitted reader
 * calls this unconditionally for every card and the modifier graph stays stable across the flip —
 * which is also what lets the transition animate.
 *
 * The earlier revision of this file recorded that a shadow mechanism had to be built first. It has
 * been: see [liftedSurface]. `AppElevation.shadow` remains `0.dp` and `AppTheme` still forces
 * `surfaceTint = Color.Transparent`, so ordinary depth is still tone alone — the lift is the one
 * exception, and it is deliberately narrow.
 */
@Composable
fun AppActiveSurface(
    active: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liftedSurface(shape = shape, lifted = active),
        content = content,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppActiveSurfacePreview() {
    AppTheme {
        AppActiveSurface(
            active = true,
            shape = AppUi.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimension.screenEdge),
        ) {
            Text(
                modifier = Modifier.padding(AppDimension.screenEdge),
                text = "Bench press",
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
        }
    }
}
