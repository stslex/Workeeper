// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toRecentItem
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Home's recent-session list, paged.
 *
 * Modelled on `all-trainings`' handler of the same name — the fourth paged list in the app and the
 * first on Home. It carries no `Handler<Action>` implementation because Home has no paging *action*:
 * `all-trainings` needs one to re-key its flow on the tag filter, and there is no filter here.
 */
@SingleIn(HomeScope::class)
internal class PagingHandler @Inject constructor(
    private val interactor: HomeInteractor,
    private val resourceWrapper: ResourceWrapper,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    store: HomeHandlerStore,
) : HomeHandlerStore by store {

    val pagingUiState: PagingUiState<PagingData<RecentSessionItem>> = PagingUiState {
        interactor.pagedRecent()
            .map { pagingData ->
                // ONE clock read per PagingData generation, not one per item.
                //
                // The relative label («вчера», «2 дня назад») is computed against a `now`, and rows
                // in one list must agree about what that is: reading the clock inside
                // `pagingData.map` reads it once per *item*, lazily, as pages load — so two rows a
                // second either side of a day boundary would print labels that contradict each
                // other in the same list.
                //
                // It is also a strict improvement on what it replaces. The unpaged path took
                // `nowMillis` off `State`, which was set on the first emission and then never
                // updated for this list, so the labels aged in place for as long as the screen
                // lived. This one refreshes whenever the pager invalidates.
                val now = System.currentTimeMillis()
                pagingData.map { session -> session.toRecentItem(now, resourceWrapper) }
            }
            .flowOn(defaultDispatcher)
    }
}
