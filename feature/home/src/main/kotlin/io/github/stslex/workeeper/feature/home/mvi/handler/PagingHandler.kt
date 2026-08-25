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
 * Home's recent-session list, paged. No `Handler<Action>` implementation: Home has no tag filter,
 * so there is no paging action to re-key the flow on.
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
                // GUARD: one clock read per PagingData generation. `pagingData.map` is lazy and
                // per-item, so a clock read inside it would give rows disagreeing relative labels.
                val now = System.currentTimeMillis()
                pagingData.map { session -> session.toRecentItem(now, resourceWrapper) }
            }
            .flowOn(defaultDispatcher)
    }
}
