// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.graphics.Color

/**
 * The fixed Wear palette of the controller redesign spec §3. Every value is copied from
 * `AppColors` dark (core:ui:kit) except [screen], which is pure black by decision D-B.
 * The accent surface is [textPrimary] on [onAccent] — accent is brightness, not hue (D-C).
 */
internal object WearPalette {

    /** Pure black, not `surfaceTier0`: the panel is OLED and lit for a whole workout (D-B). */
    val screen = Color(SCREEN)

    /** `surfaceTier2` — the interactive value-card fill. */
    val card = Color(CARD)

    /** `surfaceTier0` — the read-only card fill: the card with its fill "lost" (§4). */
    val cardInactive = Color(CARD_INACTIVE)

    /**
     * `surfaceTier4` — pending set pills. A decorative fill with no contrast obligation: the
     * set words directly below the scale carry the same information as text (§4, §10).
     */
    val pillPending = Color(PILL_PENDING)

    val textPrimary = Color(TEXT_PRIMARY)

    val textSecondary = Color(TEXT_SECONDARY)

    /** `textTertiary` — disabled and secondary labels. Never `#627587`, which fails 4.5:1 (D-G). */
    val textMuted = Color(TEXT_MUTED)

    /** `borderDefault` — a stroke only, held to the 3:1 non-text threshold, never text (D-G). */
    val stroke = Color(STROKE)

    /** Content on the accent surface, which is [textPrimary] used as a fill. */
    val onAccent = Color(ON_ACCENT)

    val error = Color(ERROR)
}

private const val SCREEN: Long = 0xFF000000
private const val CARD: Long = 0xFF1E242A
private const val CARD_INACTIVE: Long = 0xFF0B0D0F
private const val PILL_PENDING: Long = 0xFF242B32
private const val TEXT_PRIMARY: Long = 0xFFF1F5F9
private const val TEXT_SECONDARY: Long = 0xFFB7C0CA
private const val TEXT_MUTED: Long = 0xFF8B95A1
private const val STROKE: Long = 0xFF627587
private const val ON_ACCENT: Long = 0xFF0B0D0F
private const val ERROR: Long = 0xFFDF714B
