// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The app-owned back stack, wrapped so `:app:app`'s host pieces share one handle. [NavBackStack]
 * is a `SnapshotStateList`, so reads of [currentScreen] in composition subscribe to changes.
 */
@Stable
class NavigatorHolder(
    val backStack: NavBackStack<NavKey>,
) {

    /** The visible destination, typed; `null` only for a non-[Screen] key, which cannot ship. */
    val currentScreen: Screen? get() = backStack.lastOrNull() as? Screen
}
