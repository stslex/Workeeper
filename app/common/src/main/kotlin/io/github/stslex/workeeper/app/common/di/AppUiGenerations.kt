// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.common.di

import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.StateFlow

/**
 * The UI face of one runtime generation (Phase 5, `kmp-phase-5-startup-processor.md` §8.7).
 * `App()` keys its whole body on [id] and provides [viewModelStoreOwner] as the root
 * `LocalViewModelStoreOwner`, which is what makes the Nav3 back stack, the per-entry Stores,
 * `AppRootViewModel`, and the app-dialog Store generation-owned: a new generation starts at the
 * root with fresh instances from the new graph, and the old generation's store is cleared
 * deterministically by the runtime — never left to Activity death.
 */
sealed interface AppUiPhase {

    class Generation(
        val id: Int,
        val viewModelStoreOwner: ViewModelStoreOwner,
    ) : AppUiPhase

    /**
     * The transition window: no generation is published for new UI work. `App()` composes a
     * neutral interstitial (theme-independent — the theme flows from the generation's own
     * `AppRootViewModel`, which does not exist in this window).
     */
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

    /**
     * Requests admission for a generation's UI region, DURING COMPOSITION and before the region
     * resolves ANY dependency (Phase 5 R3, spec §8.4 step 1). LOAD-BEARING (no default): the
     * runtime's transition gate holds the transition open while a token is outstanding, and a
     * host that silently granted admission would let a replacement close the database under a
     * live UI.
     *
     * Returns `null` when the generation is already retired — the region must then render
     * nothing and resolve nothing, so a stale frame can never reach the graph, its Stores or
     * its ViewModels.
     */
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
