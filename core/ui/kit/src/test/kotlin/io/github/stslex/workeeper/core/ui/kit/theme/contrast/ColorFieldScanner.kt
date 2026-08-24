// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import java.lang.reflect.Method

/**
 * Enumerates a palette's `Color` properties by reflection — `Color` is a value class over
 * `ULong`, so its getters are zero-arg `long` methods with an optional mangled name suffix.
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
