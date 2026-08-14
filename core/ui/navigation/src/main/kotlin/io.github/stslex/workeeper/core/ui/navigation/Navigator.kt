// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlin.reflect.KClass

@Stable
interface Navigator {

    fun navTo(screen: Screen)

    /**
     * Pop the current destination. To hand a value back, use [popBackWithResult] — which
     * requires the destination to declare what it returns.
     */
    fun popBack()

    /**
     * Pop [destination] off the back stack, handing [result] to whoever opened it.
     *
     * The result type is not chosen here — it is read off [destination]'s
     * [ScreenWithResult] parameter, so `popBackWithResult(Screen.PlanEditor::class, "yes")`
     * does not compile: `Screen.PlanEditor` declares `ScreenWithResult<Boolean>` and `R`
     * resolves to `Boolean` before [result] is checked against it.
     *
     * Read back with `NavResults.result` / `NavResults.OnResult` in `core:ui:mvi`, keyed by
     * the same [destination] class.
     */
    fun <S, R : Any> popBackWithResult(
        destination: KClass<S>,
        result: R,
    ) where S : ScreenWithResult<R>

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
