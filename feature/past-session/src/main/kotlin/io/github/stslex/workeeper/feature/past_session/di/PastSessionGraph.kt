// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractorImpl
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/past-session's Metro graph as a CONTRIBUTED [GraphExtension] of [PastSessionScope]. The factory
 * carries `@ContributesTo(AppScope::class)`, so the extension is merged into the app graph in `:app` and
 * inherits ALL of its app-scoped bindings — the 9 formerly hand-threaded bound-instance `@Provides` are
 * gone. The two `@Binds` (PastSessionInteractor, PastSessionHandlerStore) stay.
 *
 * ROUTE ARG (shape B): the `Screen.PastSession` route arg enters as a `@Provides` bound instance on the
 * extension factory rather than as an `@Assisted` store param, so the accessor is the Store itself and
 * the feature carries no assisted machinery at all — `@AssistedInject`, `@Assisted`, `@AssistedFactory`
 * and the `StoreFactory` supertype are all gone from [PastSessionStoreImpl]. One extension is built per
 * navigation entry, parameterised by that entry's arg.
 *
 * The route arg is an ordinary binding in this scope, so it COULD be injected anywhere in the extension;
 * `ScreenInjectionRule` (detekt) forbids that outside the Store's primary constructor — state must flow
 * through the Store, not be read from DI.
 *
 * [ioDispatcher] is an observability accessor, not a feature need: past-session consumes exactly ONE
 * dispatcher (`@IODispatcher`), and an `assertSame` against the parent's cannot by itself distinguish
 * "inherited the IO key" from "the parent collapsed IO and Default into one instance". The identity test
 * in `:app` reads this accessor and asserts it is the parent's `@IODispatcher` AND *not* the parent's
 * `@DefaultDispatcher`. It costs no forced-public surface — `CoroutineDispatcher` is an external type.
 *
 * Interface + factory are `public` because `:app` generates the extension impl and references them;
 * [PastSessionScope] stays `internal` (Metro reads the scope KClass at IR level).
 */
@GraphExtension(PastSessionScope::class)
interface PastSessionGraph {

    /** Root accessor: the retained Store. Its route arg is the factory's bound instance. */
    val pastSessionStore: PastSessionStoreImpl

    /** Observability root for the qualifier-distinctness claim — see the class doc. */
    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    @Binds
    val PastSessionInteractorImpl.bindInteractor: PastSessionInteractor

    @Binds
    val PastSessionHandlerStoreImpl.bindHandlerStore: PastSessionHandlerStore

    /**
     * The creator method name must be UNIQUE across all contributed extension factories: every
     * `@ContributesTo(AppScope::class)` factory is merged into `AppGraph`, so two factories both
     * declaring `create()` collide ("return types are incompatible"). Binding rule for all 13 — see
     * documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createPastSessionGraph(@Provides screen: Screen.PastSession): PastSessionGraph
    }
}
