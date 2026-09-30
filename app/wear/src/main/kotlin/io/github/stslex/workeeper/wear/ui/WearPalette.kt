// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.graphics.Color
import io.github.stslex.workeeper.core.ui.design.DARK_BASE
import io.github.stslex.workeeper.core.ui.design.DARK_BODY
import io.github.stslex.workeeper.core.ui.design.DARK_CONTROL_OUTLINE
import io.github.stslex.workeeper.core.ui.design.DARK_MAX
import io.github.stslex.workeeper.core.ui.design.DARK_META
import io.github.stslex.workeeper.core.ui.design.DARK_RAISE
import io.github.stslex.workeeper.core.ui.design.DARK_RUST
import io.github.stslex.workeeper.core.ui.design.DARK_SLAB

/**
 * The fixed Wear palette of the controller redesign spec §3. Every value is read from
 * the shared dark tokens (core:ui:design-tokens) except [screen], which is pure black by decision D-B.
 * The accent surface is [textPrimary] on [onAccent] — accent is brightness, not hue (D-C).
 */
internal object WearPalette {

    /** Pure black, not `surfaceTier0`: the panel is OLED and lit for a whole workout (D-B). */
    val screen = Color(SCREEN)

    /** `surfaceTier2` — the interactive value-card fill. */
    val card = Color(DARK_SLAB)

    /** `surfaceTier0` — the read-only card fill: the card with its fill "lost" (§4). */
    val cardInactive = Color(DARK_BASE)

    /**
     * `surfaceTier4` — pending set pills. A decorative fill with no contrast obligation: the
     * set words directly below the scale carry the same information as text (§4, §10).
     */
    val pillPending = Color(DARK_RAISE)

    val textPrimary = Color(DARK_MAX)

    val textSecondary = Color(DARK_BODY)

    /** `textTertiary` — disabled and secondary labels. Never `#627587`, which fails 4.5:1 (D-G). */
    val textMuted = Color(DARK_META)

    /** `borderDefault` — a stroke only, held to the 3:1 non-text threshold, never text (D-G). */
    val stroke = Color(DARK_CONTROL_OUTLINE)

    /** Content on the accent surface, which is [textPrimary] used as a fill. */
    val onAccent = Color(DARK_BASE)

    val error = Color(DARK_RUST)
}

private const val SCREEN: Long = 0xFF000000
