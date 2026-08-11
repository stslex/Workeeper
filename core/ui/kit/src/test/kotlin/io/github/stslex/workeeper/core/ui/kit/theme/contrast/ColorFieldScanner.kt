// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import java.lang.reflect.Method

/**
 * Reads every `Color` property off an object by reflection, so the contrast measurement
 * enumerates the palette *mechanically* instead of from a hand-written list that silently
 * rots the moment someone adds a colour.
 *
 * `androidx.compose.ui.graphics.Color` is a `@JvmInline value class` over `ULong`, so its
 * property getters compile to no-arg methods returning primitive `long`, usually with a
 * mangled name suffix (`getPrimary-0d7_KjU`). The suffix is treated as optional so an
 * un-mangled getter is still found.
 *
 * Known limitation: a plain non-colour `Long` property would also match. That is deliberate —
 * over-reporting is safe here, because [PaletteContrastReportTest] asserts the scanned name set
 * against an explicit expected set. A false positive fails loudly; a false negative would be
 * the dangerous one, and cannot happen.
 */
internal object ColorFieldScanner {

    private val COLOR_GETTER = Regex("""^get([A-Z][A-Za-z0-9]*)(?:-.+)?$""")

    /** Property names, in stable alphabetical order, of every `Color` declared on [type]. */
    fun colorFieldNames(type: Class<*>): Set<String> = colorGetters(type).keys

    /** Property name to live value for every `Color` declared on [instance]'s class. */
    fun colorMap(instance: Any): Map<String, Color> = colorGetters(instance.javaClass)
        .mapValues { (_, getter) -> Color(value = (getter.invoke(instance) as Long).toULong()) }

    private fun colorGetters(type: Class<*>): Map<String, Method> = type.methods
        .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
        .mapNotNull { method ->
            COLOR_GETTER.matchEntire(method.name)
                ?.groupValues
                ?.get(1)
                ?.replaceFirstChar(Char::lowercaseChar)
                ?.let { name -> name to method }
        }
        .toMap(sortedMapOf())
}
