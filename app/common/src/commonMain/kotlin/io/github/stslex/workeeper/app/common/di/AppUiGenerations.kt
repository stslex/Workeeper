// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.common.di

import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.StateFlow

/** UI publication state for a runtime generation. */
sealed interface AppUiPhase {

    class Generation(
        val id: Int,
        val viewModelStoreOwner: ViewModelStoreOwner,
    ) : AppUiPhase

    /** No generation is published to new UI work. */
    data object Transitioning : AppUiPhase
}

/**
 * Held-instance seam for the generation stream, satisfied by the process `Application`: unlike
 * [AppRootDeps] it cannot live in the graph's contract — it announces graph replacement.
 */
interface AppUiGenerationsHolder {

    val appUiPhases: StateFlow<AppUiPhase>

    /** Requests composition-time admission; null means render without resolving dependencies. */
    fun admitUiGeneration(id: Int): AppUiAdmissionToken?

    /** Releases exactly the region [token] admitted. Idempotent, and ABA-safe by identity. */
    fun releaseUiGeneration(token: AppUiAdmissionToken)
}

/** An opaque admission grant for one generation UI region; identity is all `app:common` needs. */
interface AppUiAdmissionToken
