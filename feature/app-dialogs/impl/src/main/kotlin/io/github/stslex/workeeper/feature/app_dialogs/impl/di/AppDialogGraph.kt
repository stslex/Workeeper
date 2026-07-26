// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * feature/app-dialogs:impl's Metro graph as a CONTRIBUTED [GraphExtension] of [AppDialogsScope] — the
 * THIRTEENTH and final feature graph of the arc. The factory carries `@ContributesTo(AppScope::class)`,
 * so the extension is merged into the app graph in `:app` and inherits ALL of its app-scoped bindings —
 * the 5 formerly hand-threaded bound-instance `@Provides` are gone. The one `@Binds`
 * (AppDialogHandlerStore) stays.
 *
 * NO ROUTE ARG — this feature is app-root-scoped and screen-less (`AppFeature<P>`, not
 * `FeatureAssisted`), so the creator takes no parameters at all. That is the same no-arg factory form
 * the five plain `Feature<P, S>` ports used, not a new shape: shape B's route-arg variant simply does
 * not apply here, and nothing else about the pattern changes.
 *
 * **This port also retires the `AppDialogInternalsHolder` seam.** [AppDialogRepository] and
 * [AppDialogObserverImpl] are impl-owned concrete types that no other module can name, so before this
 * port they reached the feature graph through an `Application`-implements-holder trick
 * (`Context.appDialogInternals()`) rather than through `appDeps<T>()`. As a contributed extension the
 * graph inherits both directly from the parent, so the holder, its accessor and `BaseApplication`'s
 * two `get()` overrides all go. The similarly-named `AppDialogPublisherHolder` in the **api** module
 * went the same way: cross-module producers (settings / recovery) take `AppDialogPublisher` as a
 * constructor dep resolved from their own extension graph, so nothing read that seam either.
 *
 * The two accessors below are observability roots for the identity test: they are the app-scoped
 * singletons this feature used to be handed and now inherits, and asserting them against the parent's
 * instances is what distinguishes "inherited" from "rebuilt". They cost no forced-public surface —
 * both types are already public.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [AppDialogsScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(AppDialogsScope::class)
interface AppDialogGraph {

    /** Root accessor: the retained Store (plain, non-assisted). Mounted Activity-scoped via AppFeature. */
    val appDialogStore: AppDialogStoreImpl

    /** Observability roots — the two app-scoped singletons now inherited instead of handed in. */
    val appDialogRepository: AppDialogRepository

    val appDialogObserverImpl: AppDialogObserverImpl

    @Binds
    val AppDialogHandlerStoreImpl.bindHandlerStore: AppDialogHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createAppDialogGraph(): AppDialogGraph
    }
}
