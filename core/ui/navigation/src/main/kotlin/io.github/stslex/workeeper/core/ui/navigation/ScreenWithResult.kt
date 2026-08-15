// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import kotlin.reflect.KClass

/**
 * A destination that hands a value back to whoever opened it.
 *
 * Carried ONLY by destinations that actually produce a result — today
 * [Screen.PlanEditor] ([Boolean]) and [Screen.ExerciseImage] ([String]). The other ten
 * destinations stay plain [Screen] and gain nothing; a marker every destination carries
 * would mark nothing.
 *
 * **The type lives here, on the destination, not at the call site.** With [R] declared
 * here, a wrong-typed produce or read does not compile, and "who reads this, and as what"
 * is a question for the compiler rather than for a comment that nothing verifies.
 *
 * **Reading is nullable; `null` means "no result".** There is deliberately no `Cancelled`
 * case and no sealed wrapper: no consumer distinguishes "produced nothing" from "dismissed
 * without producing", so a sealed result would add branching at every read site to carry
 * information nobody reads.
 *
 * @param R what the destination hands back. Non-null: absence is expressed by the read
 * returning `null`, so a nullable [R] would make "no result" ambiguous with "a result that
 * happens to be null".
 */
interface ScreenWithResult<R : Any> : Screen

/**
 * The transport seam between a producing destination and its consumer.
 *
 * Public because the producer (`NavigatorEventBus` in `:app:app` — the [Navigator]
 * implementation, where the key is minted) and the consumer (`core:ui:mvi`'s `NavResults`)
 * live in different modules and must agree on it; it is an implementation detail of the
 * contract, not something features call. Features name [ScreenWithResult] and the typed
 * produce/read APIs — never this.
 *
 * Keyed by the destination's own qualified name, so the key cannot drift out of sync with
 * the type the way a hand-written string constant could.
 */
object NavResultKey {

    private const val PREFIX = "nav-result"

    /**
     * **The key is the [KClass] that is passed, not the destination that is popped.** A
     * sealed destination and its variant are different keys: `Screen.PlanEditor::class` and
     * `Screen.PlanEditor.Existing::class` do not name the same channel.
     *
     * Producer and consumer must therefore pass the *same* reference. `Screen.PlanEditor` is
     * the one that matters today — `plan-editor`'s `NavigationHandler` produces with the
     * sealed parent and `LiveWorkoutGraph` reads with it, while the route actually registered
     * and popped is the concrete `Existing`. That mismatch is fine, and deliberate: the
     * runtime type never enters the key.
     *
     * Narrowing either side to the variant would still compile and still typecheck — [R] is
     * identical — and the result would simply stop arriving. Per the `.catch { onError(it) }`
     * swallow in `AppCoroutineScopeImpl`, nothing would throw; the consumer would hold default
     * state. If a result ever goes missing with both sides looking correct, compare the two
     * `KClass` references first.
     */
    fun of(destination: KClass<out ScreenWithResult<*>>): String =
        "$PREFIX:${destination.qualifiedName ?: destination.toString()}"
}
