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
 * feature/past-session's Metro graph, a contributed [GraphExtension] of [PastSessionScope]. The
 * `Screen.PastSession` route arg is a bound instance on the factory, one extension per nav entry.
 */
@GraphExtension(PastSessionScope::class)
interface PastSessionGraph {

    /** Root accessor: the retained Store. */
    val pastSessionStore: PastSessionStoreImpl

    /** Observability accessor for the `:app` qualifier-distinctness test; not a feature need. */
    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    @Binds
    val PastSessionInteractorImpl.bindInteractor: PastSessionInteractor

    @Binds
    val PastSessionHandlerStoreImpl.bindHandlerStore: PastSessionHandlerStore

    /**
     * Creator name must be unique across contributed factories — they all merge into `AppGraph`.
     * See documentation/graph-extension-arc/HANDOFF.md.
     */
    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createPastSessionGraph(@Provides screen: Screen.PastSession): PastSessionGraph
    }
}
