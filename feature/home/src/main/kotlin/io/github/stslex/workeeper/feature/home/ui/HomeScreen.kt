package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen
import io.github.stslex.workeeper.feature.home.di.HomeFeature
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.homeGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navScreen<Screen.Home> {
        HomeScreen(
            modifier = modifier,
            sharedTransitionScope = sharedTransitionScope,
            component = remember { HomeComponent.create(navigator) },
            animatedContentScope = this
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeScreen(
    component: HomeComponent,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier
) {
    NavComponentScreen(HomeFeature, component) { processor ->

        val items = remember { processor.state.value.items.invoke() }.collectAsLazyPagingItems()
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is HomeStore.Event.HapticFeedback -> haptic.performHapticFeedback(event.type)
            }
        }

        val lazyListState = rememberLazyListState()
        HomeWidget(
            modifier = modifier,
            lazyPagingItems = items,
            lazyState = lazyListState,
            consume = processor::consume,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            selectedItems = processor.state.value.selectedItems
        )
    }
}
