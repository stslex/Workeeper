// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractorImpl
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/past-session, scoped to [PastSessionScope].
 *
 * ASSISTED Store: `PastSessionStoreImpl` takes the `Screen.PastSession` route arg via `@Assisted`,
 * so the graph exposes the assisted [PastSessionStoreImpl.Factory] as its root — never the Store.
 *
 * The 9 app-scoped deps are `@SingleIn(AppScope)` bindings from the app graph, handed in as
 * `@Provides` bound instances. `@IODispatcher` stays QUALIFIED, the only dispatcher (no collision).
 * No Context.
 */
@DependencyGraph(scope = PastSessionScope::class)
internal interface PastSessionGraph {

    /** Root accessor: the ASSISTED store factory. `create(screen)` builds the retained Store. */
    val storeFactory: PastSessionStoreImpl.Factory

    @Binds
    val PastSessionInteractorImpl.bindInteractor: PastSessionInteractor

    @Binds
    val PastSessionHandlerStoreImpl.bindHandlerStore: PastSessionHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides sessionRepository: SessionRepository,
            @Provides setRepository: SetRepository,
            @Provides personalRecordRepository: PersonalRecordRepository,
            @Provides resourceWrapper: ResourceWrapper,
            @Provides navigator: Navigator,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @IODispatcher ioDispatcher: CoroutineDispatcher,
        ): PastSessionGraph
    }
}
