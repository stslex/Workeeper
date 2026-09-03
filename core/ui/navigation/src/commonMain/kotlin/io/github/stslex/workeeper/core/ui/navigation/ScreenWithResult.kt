// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import kotlin.reflect.KClass

/**
 * A destination that hands a value back to whoever opened it. Reading is nullable — `null` means
 * "no result" — which is why [R] is bounded non-null.
 */
interface ScreenWithResult<R : Any> : Screen

/**
 * Transport key seam between a producing destination and its consumer, keyed by the destination's
 * qualified name. Features name [ScreenWithResult] and the typed APIs, never this.
 */
object NavResultKey {

    private const val PREFIX = "nav-result"

    /**
     * GUARD: the key is the [KClass] passed, not the route popped — producer and consumer must
     * pass the SAME reference; narrowing to a sealed variant compiles and silently drops results.
     */
    fun of(destination: KClass<out ScreenWithResult<*>>): String =
        "$PREFIX:${destination.qualifiedName ?: destination.toString()}"
}
