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
 * **The type lives here, on the destination, not at the call site.** That is the whole
 * point of the interface. `Screen.PlanEditor`'s KDoc once claimed three consumers and had
 * exactly one — wrong for months, and found only by grep. With [R] declared here, a
 * wrong-typed produce or read does not compile, and "who reads this, and as what" becomes
 * a question for the compiler rather than for a comment that nothing verifies.
 *
 * **Reading is nullable; `null` means "no result".** There is deliberately no `Cancelled`
 * case and no sealed wrapper. Before this contract, `planEditorSavedAttr` defaulted to
 * `false` and `exerciseImageRequestAttr` to `null` — "did not save" and "pressed back"
 * were already the same state, and no consumer distinguished them. A sealed result would
 * split apart something nothing reads and add branching for zero information.
 *
 * @param R what the destination hands back. Non-null: absence is expressed by the read
 * returning `null`, so a nullable [R] would make "no result" ambiguous with "a result that
 * happens to be null".
 */
interface ScreenWithResult<R : Any> : Screen

/**
 * The transport seam between a producing destination and its consumer.
 *
 * Public because the producer ([Navigator]) and the consumer (`core:ui:mvi`'s `NavResults`)
 * live in different modules and must agree on it; it is an implementation detail of the
 * contract, not something features call. Features name [ScreenWithResult] and the typed
 * produce/read APIs — never this.
 *
 * Keyed by the destination's own qualified name, so the key cannot drift out of sync with
 * the type the way a hand-written string constant could.
 */
object NavResultKey {

    private const val PREFIX = "nav-result"

    fun of(destination: KClass<out ScreenWithResult<*>>): String =
        "$PREFIX:${destination.qualifiedName ?: destination.toString()}"
}
