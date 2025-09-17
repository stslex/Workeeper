package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.ui.components.SingleTrainingItemWidget
import io.github.stslex.workeeper.feature.all_trainings.ui.components.TrainingFloatingActionButton
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun AllTrainingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier
) {

    val items = remember { state.pagingUiState() }.collectAsLazyPagingItems()

    Scaffold(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize(),
        floatingActionButton = {
            TrainingFloatingActionButton(
                isDeletingMode = state.selectedItems.isNotEmpty()
            ) {
                consume(Action.Click.ActionButton)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = items.itemCount
                ) { index ->
                    items[index]?.let { item ->
                        SingleTrainingItemWidget(
                            item = item,
                            isSelected = state.selectedItems.contains(item.uuid),
                            onItemClick = { consume(Action.Click.TrainingItemClick(item.uuid)) },
                            onItemLongClick = { consume(Action.Click.TrainingItemClick(item.uuid)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
internal fun AllTrainingsScreenPreview() {
    AppTheme {
        val items = Array(10) { index ->
            val labels = Array(10) {
                "label${it + index}"
            }.toImmutableList()
            val uuids = Array(10) {
                "uuid${it + index}"
            }.toImmutableList()
            TrainingUiModel(
                uuid = "uuid$index",
                name = "trainingName$index",
                labels = labels,
                exerciseUuids = uuids,
                date = DateProperty.new(System.currentTimeMillis()),
            )
        }.toList()

        val itemsPaging = {
            flowOf(PagingData.from(items))
        }
        val state = State(
            pagingUiState = itemsPaging,
            query = "",
            selectedItems = persistentSetOf("uuid1", "uuid2")
        )
        AllTrainingsScreen(
            state = state,
            consume = {}
        )
    }
}