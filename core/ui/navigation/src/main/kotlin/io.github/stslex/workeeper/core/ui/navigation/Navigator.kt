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

    fun restartApp()

    /**
     * Launch the Scenario 2 fallback `RecoveryActivity` (FQCN in
     * `feature/recovery`, manifest entry in `app/app`). The current
     * [Activity][android.app.Activity] should `finish()` after dispatching;
     * the fresh task replaces the current one via `FLAG_ACTIVITY_NEW_TASK`.
     *
     * **Bootstrap-context caveat.** This method dispatches via the
     * `NavCommand.OpenRecovery` flow, which is only processed by the
     * composable-mounted `NavigationEventBusSetup`. Callers running BEFORE
     * any UI composition (notably `MainActivity.onCreate`'s
     * Scenario 2 routing branch — fires before `setContent { App() }`)
     * MUST use a direct `Intent` launch instead — the `MutableSharedFlow(
     * replay = 0)` will silently drop an emission with no attached subscriber.
     * See `documentation/feature-specs/backup-recovery.md` → "OpenRecovery
     * contract".
     */
    fun openRecovery()
}
