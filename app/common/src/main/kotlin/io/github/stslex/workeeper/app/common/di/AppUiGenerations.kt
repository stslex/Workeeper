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

    /** The generation region entered composition. */
    fun onUiGenerationAttached(id: Int) {}

    /** The generation region left composition (all its stores/effects disposed). */
    fun onUiGenerationDisposed(id: Int) {}
}
