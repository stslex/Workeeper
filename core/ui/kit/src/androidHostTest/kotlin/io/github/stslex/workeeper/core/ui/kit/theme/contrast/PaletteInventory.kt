// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import io.github.stslex.workeeper.core.ui.kit.theme.AppColors

/**
 * Every colour slot in the palette, found by reflection so a new slot cannot be forgotten.
 * [ContrastGateTest] insists every combination this returns is accounted for.
 */
internal object PaletteInventory {

    /** Package prefix a nested colour group must live in to be recursed into. */
    private const val THEME_PACKAGE = "io.github.stslex.workeeper.core.ui.kit.theme"

    /**
     * Fully-qualified slot name to value, e.g. `surfaceTier1`, `setType.failureForeground`.
     * Nested groups are discovered by the walk, never listed.
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

    /** Every property of [instance] that is itself a theme-package colour group, at any depth. */
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
 * What a slot *is*, which decides whether and how it is measured. Derived from call sites,
 * never from the slot's name. See documentation/design-system.md.
 */
internal enum class SlotRole {

    /** Painted as text or an icon. Scored against every surface it can co-occur with. */
    FOREGROUND,

    /** Painted as a container fill. Can host foregrounds. */
    SURFACE,

    /** Both, at different call sites. Enumerated on both sides. */
    BOTH,

    /**
     * Decorative: a separator, a hairline, a reinforcing border. Takes **no** threshold —
     * WCAG 1.4.11 exempts decoration, and these are meant to be barely there.
     */
    DECORATIVE,

    /** Inactive: WCAG 1.4.3 and 1.4.11 both carve out inactive components. */
    EXEMPT,

    /** No readers. Not measured because nothing renders it. */
    DEAD,
}
