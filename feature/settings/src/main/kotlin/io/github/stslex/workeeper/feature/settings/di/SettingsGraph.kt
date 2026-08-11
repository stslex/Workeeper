// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractorImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractorImpl
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/settings' Metro graph as a CONTRIBUTED [GraphExtension] of [SettingsScope]. The factory
 * carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and
 * inherits ALL of its app-scoped bindings — all **18** formerly hand-threaded bound-instance `@Provides`
 * are gone and `createSettingsGraph()` takes no arguments. The three `@Binds` (SettingsInteractor,
 * BackupInteractor, SettingsHandlerStore) stay. [settingsStore] is the root.
 *
 * This is the widest graph in the repo, and it is what makes the inheritance claim non-trivial — every
 * category the arc has to carry appears here at once:
 * - **two same-typed qualified dispatchers**, `@DefaultDispatcher` + `@IODispatcher`, which must inherit
 *   as two DISTINCT `(CoroutineDispatcher + qualifier)` keys and not cross-wire;
 * - a **bare, unqualified `Context`**, inherited from AppGraph's `create(applicationContext)` bound
 *   instance rather than passed per-graph;
 * - `appDialogPublisher`, which reaches the extension as an ordinary `@ContributesBinding(AppScope)`
 *   binding — under the old factory it was composition-sourced through the feature-api holder seam.
 *
 * The three accessors below keep that claim OBSERVABLE. They were bridge-observability roots for
 * `SettingsGraphBridgeTest`; they are retained deliberately because the property they expose got
 * HARDER to verify, not easier — the pair is now inherited across a graph boundary instead of handed
 * in explicitly, and a silent cross-wire would be invisible from the Store alone. Read by
 * `SettingsExtensionIdentityTest` in `:app`. They cost no forced-public surface: `CoroutineDispatcher`
 * and `Context` are external types.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [SettingsScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(SettingsScope::class)
interface SettingsGraph {

    /** Root accessor: the retained Store. Metro constructs [SettingsStoreImpl], wiring its deps. */
    val settingsStore: SettingsStoreImpl

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    val appContext: Context

    @Binds
    val SettingsInteractorImpl.bindSettingsInteractor: SettingsInteractor

    @Binds
    val BackupInteractorImpl.bindBackupInteractor: BackupInteractor

    @Binds
    val SettingsHandlerStoreImpl.bindHandlerStore: SettingsHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createSettingsGraph(): SettingsGraph
    }
}
