package io.github.stslex.workeeper.feature.all_trainings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
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

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize(),
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
                        onItemLongClick = { consume(Action.Click.TrainingItemLongClick(item.uuid)) }
                    )
                }
            }
        }
        TrainingFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppDimension.Padding.big),
            isDeletingMode = state.selectedItems.isNotEmpty()
        ) {
            consume(Action.Click.ActionButton)
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
                date = PropertyHolder.DateProperty().update(System.currentTimeMillis()),
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