// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import io.github.stslex.workeeper.core.ui.kit.theme.AppColors

/**
 * Every colour slot in the palette, found by reflection rather than by a hand-written list.
 *
 * The list is mechanical on purpose. A hand-written inventory is a comment: it is correct on
 * the day it is written and silently wrong the first time somebody adds a slot. [ContrastGateTest]
 * asks this object what exists and then insists that every combination be accounted for, so a
 * new slot arrives as a test failure rather than as an unmeasured pair.
 */
internal object PaletteInventory {

    /** Nested colour groups are scanned too — the palette is not flat. */
    private fun groups(colors: AppColors): List<Pair<String, Any>> = listOf(
        "" to colors,
        "setType." to colors.setType,
        "status." to colors.status,
        "molten." to colors.molten,
        "record." to colors.record,
    )

    /**
     * Fully-qualified slot name to value, e.g. `surfaceTier1`, `setType.failureForeground`.
     *
     * `isDark` is a `Boolean`, not a `Color`, so the scanner never sees it.
     */
    fun slots(colors: AppColors): Map<String, Color> = groups(colors)
        .flatMap { (prefix, instance) ->
            ColorFieldScanner.colorMap(instance).map { (name, value) -> "$prefix$name" to value }
        }
        .toMap(sortedMapOf())
}

/**
 * What a slot *is*, which decides whether and how it is measured.
 *
 * Derived from call sites, not from the slot's name — the recon pass found three slots whose
 * names lie about their role (`record.border` fills a pill, `accentTintedForeground` fills a
 * circle, `borderSubtle` paints a Box that is functionally a rule).
 */
internal enum class SlotRole {

    /** Painted as text or an icon. Scored against every surface it can co-occur with. */
    FOREGROUND,

    /** Painted as a container fill. Can host foregrounds. */
    SURFACE,

    /** Both, at different call sites. Enumerated on both sides. */
    BOTH,

    /**
     * Decorative: a separator, a hairline, a reinforcing border. Takes **no** threshold.
     *
     * v3 separates sections with a 30px gutter and a label, not with a line (spec §3.1), so a
     * hairline carries no information a user could lose. WCAG 1.4.11 exempts decoration
     * explicitly. Scoring these would be theatre: every one of them fails 3:1 by construction
     * (dark `hair-s` on `slab` is 1.22:1) because they are *meant* to be barely there.
     */
    DECORATIVE,

    /**
     * Inactive. WCAG 1.4.3 and 1.4.11 both carve out "inactive user interface components", so
     * these carry no contrast obligation at all.
     */
    EXEMPT,

    /** No readers. Not measured because nothing renders it. */
    DEAD,
}
