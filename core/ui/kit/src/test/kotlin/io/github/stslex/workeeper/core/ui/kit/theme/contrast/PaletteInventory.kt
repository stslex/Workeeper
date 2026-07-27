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

    /**
     * Package prefix a nested colour group must live in to be recursed into. Keeps the walk
     * inside the design system instead of descending into Compose or the JDK.
     */
    private const val THEME_PACKAGE = "io.github.stslex.workeeper.core.ui.kit.theme"

    /**
     * Fully-qualified slot name to value, e.g. `surfaceTier1`, `setType.failureForeground`.
     *
     * Nested groups are **discovered**, not listed. The previous version named the four groups
     * it knew about, which reintroduced exactly the failure this file exists to prevent: a
     * fifth group added to [AppColors] would have been skipped by the walk, its colours would
     * never have reached `ContrastContract.ROLES`, and the gate would have stayed green while
     * an entire group went unclassified. A hard-coded list of the things you must not forget is
     * not a safeguard.
     *
     * `isDark` is a `Boolean`, so it is neither a colour nor a group and is never visited.
     */
    fun slots(colors: AppColors): Map<String, Color> {
        val out = sortedMapOf<String, Color>()
        collect(prefix = "", instance = colors, into = out, seen = mutableSetOf())
        return out
    }

    private fun collect(
        prefix: String,
        instance: Any,
        into: MutableMap<String, Color>,
        seen: MutableSet<Class<*>>,
    ) {
        if (!seen.add(instance.javaClass)) return
        ColorFieldScanner.colorMap(instance).forEach { (name, value) ->
            into["$prefix$name"] = value
        }
        nestedGroups(instance).forEach { (name, child) ->
            collect("$prefix$name.", child, into, seen)
        }
    }

    /**
     * Every property of [instance] that is itself a colour group — i.e. a theme-package type
     * that declares at least one `Color`. Depth is unbounded, so a group nested inside a group
     * is still found.
     */
    private fun nestedGroups(instance: Any): List<Pair<String, Any>> = instance.javaClass.methods
        .filter { method ->
            method.parameterCount == 0 &&
                method.name.startsWith("get") &&
                method.returnType.name.startsWith(THEME_PACKAGE) &&
                ColorFieldScanner.colorFieldNames(method.returnType).isNotEmpty()
        }
        .mapNotNull { method ->
            val name = method.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar)
            method.invoke(instance)?.let { child -> name to child }
        }
        .sortedBy { it.first }
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
