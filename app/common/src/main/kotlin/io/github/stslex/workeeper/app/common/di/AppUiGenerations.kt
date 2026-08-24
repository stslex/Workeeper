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
 * Held-instance seam for the generation stream, same typed-point-acquisition idiom as
 * [AppRootDepsHolder] and for the same layering reason: this module sits below the graph AND
 * below the runtime, so it names a contract and the process `Application` satisfies it. Unlike
 * [AppRootDeps] this cannot be a member of the graph's contract — the stream must OUTLIVE every
 * graph, because it is what announces graph replacement.
 *
 * The attach/dispose callbacks close the loop the runtime's Quiescing stage awaits: the old
 * generation's region signals its own departure from composition, which is the only honest
 * "the UI let go" signal (a `StateFlow` write cannot know when composition applied it).
 */
interface AppUiGenerationsHolder {

    val appUiPhases: StateFlow<AppUiPhase>

    /** Requests composition-time admission; null means render without resolving dependencies. */
    fun admitUiGeneration(id: Int): AppUiAdmissionToken?

    /**
     * Releases exactly the region [token] admitted. Idempotent, and ABA-safe by identity: a
     * token released after its generation was retired (and its id possibly reopened) cancels
     * out nothing, so it can never release a LATER region's admission.
     */
    fun releaseUiGeneration(token: AppUiAdmissionToken)
}

/**
 * An opaque admission grant for one generation UI region. The runtime owns the implementation;
 * `app:common` only needs identity, which is what makes release ABA-safe.
 */
interface AppUiAdmissionToken
