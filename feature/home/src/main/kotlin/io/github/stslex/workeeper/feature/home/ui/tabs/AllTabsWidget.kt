package io.github.stslex.workeeper.feature.home.ui.tabs

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.components.ExercisePagingItem
import io.github.stslex.workeeper.feature.home.ui.components.SearchWidget
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AllTabsWidget(
    lazyPagingItems: LazyPagingItems<ExerciseUiModel>,
    selectedItems: ImmutableSet<ExerciseUiModel>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    query: String,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        SearchWidget(
            modifier = Modifier
                .padding(16.dp),
            query = query,
            onQueryChange = { consume(Action.Input.SearchQuery(it)) }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyState
        ) {
            items(
                count = lazyPagingItems.itemCount,
                key = lazyPagingItems.itemKey { it.uuid }
            ) { index ->
                lazyPagingItems[index]?.let { item ->
                    with(sharedTransitionScope) {
                        ExercisePagingItem(
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = sharedTransitionScope.rememberSharedContentState(item.uuid),
                                    animatedVisibilityScope = animatedContentScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                ),
                            item = item,
                            isSelected = selectedItems.contains(item),
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
                sets = index,
                reps = index,
                weight = 60.0 + index,
                dateProperty = DateProperty.new(System.currentTimeMillis())
            )
        }.toList()
        val pagingData = remember { flowOf(PagingData.from(items)) }.collectAsLazyPagingItems()
        AnimatedContent("") {
            SharedTransitionScope {
                AllTabsWidget(
                    lazyPagingItems = pagingData,
                    selectedItems = persistentSetOf(),
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    query = "",
                    consume = {},
                    lazyState = LazyListState(),
                    modifier = it
                )
            }
        }
    }
}