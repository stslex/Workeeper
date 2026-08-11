// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractor
import io.github.stslex.workeeper.feature.archive.domain.ArchiveInteractorImpl
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl

/**
 * feature/archive's Metro graph as a CONTRIBUTED [GraphExtension] of [ArchiveScope]. The factory carries
 * `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and inherits
 * ALL of its app-scoped bindings — the 8 formerly hand-threaded bound-instance `@Provides` are gone and
 * `createArchiveGraph()` takes no arguments. The two `@Binds` (interactor, handler store) stay.
 *
 * The two handler-store accessors below are OBSERVABILITY roots, not production call sites: they expose
 * the two binding keys the MVI wiring silently depends on collapsing to one instance, so a test can see
 * the `@SingleIn(ArchiveScope::class)` sharing invariant that `@ViewModelScoped` used to carry. Read by
 * `ArchiveExtensionIdentityTest` in `:app` — see [handlerStoreByInterfaceKey].
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [ArchiveScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(ArchiveScope::class)
interface ArchiveGraph {

    /** Root accessor: the retained Store. Metro constructs [ArchiveStoreImpl], wiring its deps. */
    val archiveStore: ArchiveStoreImpl

    /**
     * OBSERVABILITY accessor — the CONCRETE handler-store key, the one [ArchiveStoreImpl] injects as its
     * `storeEmitter`. Paired with [handlerStoreByInterfaceKey] below; see that KDoc for why both exist.
     * Costs no forced-public surface: [ArchiveHandlerStoreImpl] is already public (its `@Binds` receiver
     * is declared on this public interface).
     */
    val handlerStoreByConcreteKey: ArchiveHandlerStoreImpl

    /**
     * OBSERVABILITY accessor — the INTERFACE handler-store key, the one every archive `*Handler` injects
     * (`store: ArchiveHandlerStore`, delegated to with `by store`).
     *
     * These two accessors are the only way to make the SHARING invariant observable from a test. The
     * MVI wiring depends on the concrete key ([ArchiveStoreImpl]'s `storeEmitter`) and the interface key
     * (the handlers') resolving to ONE object: `BaseStore.init {}` calls `setStore(this)` on the emitter
     * it was given, and every handler's `updateState` / `sendEvent` reads `BaseHandlerStore.store`'s
     * `requireNotNull(_store)`. `@SingleIn(ArchiveScope::class)` on [ArchiveHandlerStoreImpl] is the ONLY
     * thing making them one object — drop it and both keys stay legal bindings that each construct their
     * own instance, so nothing fails to compile and the screen crashes on first action instead.
     * `ArchiveExtensionIdentityTest` in `:app` asserts these two resolve `===`.
     */
    val handlerStoreByInterfaceKey: ArchiveHandlerStore

    // --- @Binds migrated from the deleted ArchiveModule (abstract property, impl → interface) ---
    @Binds
    val ArchiveInteractorImpl.bindInteractor: ArchiveInteractor

    @Binds
    val ArchiveHandlerStoreImpl.bindHandlerStore: ArchiveHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createArchiveGraph(): ArchiveGraph
    }
}
