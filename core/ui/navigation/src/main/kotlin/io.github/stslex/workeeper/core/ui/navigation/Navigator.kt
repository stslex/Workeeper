// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {

    fun navTo(screen: Screen)

    fun popBack(vararg previousStackAttr: Pair<String, Any?>)

    /**
     * Navigate to [screen] and pop the current destination off the back stack. After this
     * call, the back stack tip is [screen]; the back gesture from [screen] lands on what
     * was below the popped destination.
     *
     * Used when a screen finishes a one-shot operation and wants to redirect the user
     * forward without leaving the now-stale screen behind (e.g. Live workout → Past
     * session detail after finish).
     */
    fun replaceTo(screen: Screen)
}
