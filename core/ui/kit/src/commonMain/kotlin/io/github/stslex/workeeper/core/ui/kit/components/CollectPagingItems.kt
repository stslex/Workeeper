// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems

/**
 * The one supported way to collect a [PagingUiState]; the `remember` keys on the instance,
 * since calling the fun interface inline rebuilds the flow and resets the list to Loading.
 */
@Composable
fun <T : Any> PagingUiState<PagingData<T>>.collectAsItems(): LazyPagingItems<T> =
    remember(this) { this() }.collectAsLazyPagingItems()
