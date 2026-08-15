// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The app-owned back stack, wrapped so `:app:app`'s host pieces share one handle.
 *
 * Under Nav2 this held a `NavHostController`; under Nav3 the stack itself is the state —
 * [NavBackStack] is a `SnapshotStateList`, so reads of [currentScreen] inside composition
 * subscribe to changes, which is what replaced `OnDestinationChangedListener` for the bottom
 * bar and the focus-clear effect.
 *
 * This type is the widest library-facing seam in the module (the Nav2 holder exposed a raw
 * controller with no comment — filed and fixed here): the stack is exposed for `:app:app`'s
 * command executor and host ONLY. Features navigate through [Navigator]; no feature module may
 * name a `NavBackStack`, for the same reason none names an `EntryProviderScope` — see
 * [NavGraphScope]'s KDoc.
 */
@Stable
class NavigatorHolder(
    val backStack: NavBackStack<NavKey>,
) {

    /** The visible destination, typed; `null` only for a non-[Screen] key, which cannot ship. */
    val currentScreen: Screen? get() = backStack.lastOrNull() as? Screen
}
