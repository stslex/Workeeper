// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import kotlinx.coroutines.flow.flowOf

/**
 * A [HomeStore.State] with an empty paged list, for handler tests. A function, not a `val`, so a
 * test that collects the flow cannot leave a terminated one behind for the next case.
 */
internal fun emptyPagingState(): HomeStore.State = HomeStore.State.init(
    pagingUiState = PagingUiState { flowOf(PagingData.empty<RecentSessionItem>()) },
)
