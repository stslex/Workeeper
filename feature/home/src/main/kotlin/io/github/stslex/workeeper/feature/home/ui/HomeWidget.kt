package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.components.HomeActionButton
import io.github.stslex.workeeper.feature.home.ui.components.ExercisePagingItem
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@Composable
fun HomeWidget(
    lazyPagingItems: LazyPagingItems<ExerciseUiModel>,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = { HomeActionButton { consume(Action.Click.ButtonAddClick) } }
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
                        ExercisePagingItem(item)
                    }
                }
            }
        }
    }
}

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
                weight = 60 + index,
                timestamp = System.currentTimeMillis()
            )
        }.toList()
        val pagingData = remember { flowOf(PagingData.from(items)) }.collectAsLazyPagingItems()
        HomeWidget(
            lazyPagingItems = pagingData,
            consume = {},
            lazyState = rememberLazyListState(),
        )
    }
}