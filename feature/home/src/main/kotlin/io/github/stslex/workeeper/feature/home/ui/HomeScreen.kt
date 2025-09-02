package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.feature.home.di.HomeFeature
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent

@Composable
fun HomeScreen(
    component: HomeComponent,
    modifier: Modifier = Modifier
) {
    NavComponentScreen(HomeFeature, component) { processor ->

        val items = remember { processor.state.value.items.invoke() }.collectAsLazyPagingItems()

        processor.Handle { event -> }

        val lazyListState = rememberLazyListState()

        HomeWidget(
            modifier = modifier,
            lazyPagingItems = items,
            lazyState = lazyListState,
            consume = processor::consume
        )
    }
}
