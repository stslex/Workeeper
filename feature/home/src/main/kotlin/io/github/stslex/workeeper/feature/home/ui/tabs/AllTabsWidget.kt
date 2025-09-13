package io.github.stslex.workeeper.feature.home.ui.tabs

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.components.ExercisePagingItem
import io.github.stslex.workeeper.feature.home.ui.components.HomeAllEmptyWidget
import io.github.stslex.workeeper.feature.home.ui.components.SearchWidget
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeAllState
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AllTabsWidget(
    state: HomeAllState,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val items = remember { state.items.invoke() }.collectAsLazyPagingItems()
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        SearchWidget(
            modifier = Modifier
                .padding(16.dp),
            query = state.query,
            onQueryChange = { consume(Action.Input.SearchQuery(it)) }
        )
        Box {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyState
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.uuid }
                ) { index ->
                    items[index]?.let { item ->
                        with(sharedTransitionScope) {
                            ExercisePagingItem(
                                modifier = Modifier
                                    .sharedBounds(
                                        sharedContentState = sharedTransitionScope.rememberSharedContentState(
                                            item.uuid
                                        ),
                                        animatedVisibilityScope = animatedContentScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                    ),
                                item = item,
                                isSelected = state.selectedItems.contains(item),
                                onClick = {
                                    consume(Action.Click.Item(item))
                                },
                                onLongClick = {
                                    consume(Action.Click.LonkClick(item))
                                }
                            )
                        }
                    }
                }
            }

            if (
                items.loadState.refresh is LoadState.NotLoading &&
                items.loadState.append is LoadState.NotLoading &&
                items.loadState.prepend is LoadState.NotLoading &&
                items.itemCount == 0
            ) {
                HomeAllEmptyWidget(
                    query = state.query
                )
            }
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
private fun AllTabsWidgetPreview() {
    AppTheme {
        val items = Array(10) { index ->
            ExerciseUiModel(
                uuid = Uuid.random().toString(),
                name = "nameOfExercise$index",
                dateProperty = DateProperty.new(System.currentTimeMillis())
            )
        }.toList()
        val itemsPaging = {
            flowOf(PagingData.from(items))
        }
        val state = HomeAllState(
            items = itemsPaging,
            selectedItems = persistentSetOf(),
            query = ""
        )
        AnimatedContent("") {
            SharedTransitionScope {
                AllTabsWidget(
                    state = state,
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    consume = {},
                    lazyState = LazyListState(),
                    modifier = it
                )
            }
        }
    }
}