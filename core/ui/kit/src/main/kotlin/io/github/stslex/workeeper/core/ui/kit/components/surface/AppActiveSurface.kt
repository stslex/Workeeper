// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * Because raisedness is not a colour. The v3 mockups express it as a surface **plus a shadow**
 * (`.card.active` is `--slab` + `--slabtop`), and this app expresses it today as an animated accent
 * **border** on the ordinary card tier (`LiveExerciseCard`). A rule that counted reads of one
 * palette slot would pass three raised cards that all used surface-plus-shadow, and fail an
 * innocent chip that happened to share a hex. Counting call sites of the treatment is the check
 * that matches the invariant.
 *
 * Note also that the token named `raise` is a red herring here: in both mockups `--raise` is a
 * *utility* fill — the empty progress track, the selected tag, a hover state — and never a panel.
 * `surfaceTier4` and `accentTintedBackground` carry that hex in exactly that utility role and are
 * deliberately **not** under this rule.
 *
 * ## Why this is a pass-through today
 *
 * What actually expresses "active" is decided in step 5, with the session mockup in hand. Choosing
 * it here — from a mockup that opens two cards at once and a brief that names a token the mockup
 * uses for something else — would be guessing, and a guess baked into the one composable everything
 * else will point at is expensive to undo.
 *
 * So this is deliberately thin, and right now it is visually indistinguishable from its content.
 * That does not weaken the rule: one call site of a placeholder is still one call site, and the
 * gate is in place before there is anything to guard, rather than after.
 *
 * **If step 5 lands on surface-plus-shadow, a shadow mechanism has to be built first.** There is no
 * z-elevation in this project today: `AppElevation` maps all six levels onto `surfaceTier0..4` with
 * `shadow = 0.dp`, and `AppTheme.kt:91` forces `surfaceTint = Color.Transparent`. Depth is
 * currently expressed entirely by tone.
 */
@Composable
fun AppActiveSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
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
            modifier = Modifier
                .fillMaxWidth()
                .background(AppUi.colors.surfaceTier1)
                .padding(AppDimension.screenEdge),
        ) {
            Text(
                text = "Bench press",
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
        }
    }
}
