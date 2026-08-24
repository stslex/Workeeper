// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlin.reflect.KClass

@Stable
interface Navigator {

    fun navTo(screen: Screen)

    /** Pop the current destination; to hand a value back use [popBackWithResult]. */
    fun popBack()

    /**
     * Pop [destination], handing [result] to whoever opened it. The result type comes from
     * [destination]'s [ScreenWithResult] parameter; read back with `NavResults` in `core:ui:mvi`.
     */
    fun <S, R : Any> popBackWithResult(
        destination: KClass<S>,
        result: R,
    ) where S : ScreenWithResult<R>

    /** Navigate to [screen] and pop the current destination, leaving no stale screen behind. */
    fun replaceTo(screen: Screen)

    fun restartApp()

    /**
     * Launch the fallback `RecoveryActivity`. GUARD: before any UI composition the bus has no
     * subscriber and drops this silently — launch the `Intent` directly. See `backup-recovery.md`.
     */
    fun openRecovery()
}
