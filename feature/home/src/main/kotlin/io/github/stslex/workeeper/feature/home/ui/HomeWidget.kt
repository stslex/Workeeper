package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.components.ExercisePagingItem
import io.github.stslex.workeeper.feature.home.ui.components.HomeActionButton
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeWidget(
    lazyPagingItems: LazyPagingItems<ExerciseUiModel>,
    selectedItems: ImmutableSet<ExerciseUiModel>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            with(sharedTransitionScope) {
                HomeActionButton(
                    modifier = Modifier
                        .sharedBounds(
                            sharedContentState = sharedTransitionScope.rememberSharedContentState("createExercise"),
                            animatedVisibilityScope = animatedContentScope,
                            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                        ),
                    selectedMode = selectedItems.isNotEmpty()
                ) {
                    consume(Action.Click.FloatButtonClick)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = "HOME",
                style = MaterialTheme.typography.labelLarge
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
private fun HomeWidgetPreview() {
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

//        HomeWidget(
//            lazyPagingItems = pagingData,
//            consume = {},
//            lazyState = rememberLazyListState(),
//            sharedTransitionScope = sharedTransitionScope,
//            animatedContentScope = animatedContentScope
//        )

    }
}