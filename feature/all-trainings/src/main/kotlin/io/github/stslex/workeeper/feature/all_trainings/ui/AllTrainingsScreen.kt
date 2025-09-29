package io.github.stslex.workeeper.feature.all_trainings.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.buttons.AppActionButton
import io.github.stslex.workeeper.core.ui.kit.components.search.SearchPagingWidget
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.model.ItemPosition
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.ui.components.EmptyWidget
import io.github.stslex.workeeper.feature.all_trainings.ui.components.SingleTrainingItemWidget
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AllTrainingsScreen(
    state: State,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember { state.pagingUiState() }.collectAsLazyPagingItems()
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            SearchPagingWidget(
                modifier = Modifier
                    .padding(AppDimension.Padding.big),
                query = state.query,
                onQueryChange = { consume(Action.Input.SearchQuery(it)) },
            )
            Spacer(Modifier.height(AppDimension.Padding.big))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(
                            topStart = MaterialTheme.shapes.extraLarge.topStart,
                            topEnd = MaterialTheme.shapes.extraLarge.topEnd,
                            bottomEnd = CornerSize(0.dp),
                            bottomStart = CornerSize(0.dp),
                        ),
                    )
                    .padding(AppDimension.Padding.big),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large),
                ) {
                    items(
                        count = items.itemCount,
                    ) { index ->
                        items[index]?.let { item ->
                            SingleTrainingItemWidget(
                                item = item,
                                isSelected = state.selectedItems.contains(item.uuid),
                                onClick = { consume(Action.Click.TrainingItemClick(item.uuid)) },
                                onLongClick = { consume(Action.Click.TrainingItemLongClick(item.uuid)) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope,
                                itemPosition = ItemPosition.getItemPosition(
                                    index = index,
                                    itemsCount = items.itemCount,
                                ),
                            )
                        }
                    }
                }

                if (
                    items.loadState.refresh is LoadState.NotLoading &&
                    items.loadState.append is LoadState.NotLoading &&
                    items.loadState.prepend is LoadState.NotLoading &&
                    items.itemCount == 0
                ) {
                    EmptyWidget(query = state.query)
                }
            }
        }
        with(sharedTransitionScope) {
            AppActionButton(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = sharedTransitionScope.rememberSharedContentState(
                            "createExercise",
                        ),
                        animatedVisibilityScope = animatedContentScope,
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                            ContentScale.Inside,
                            Alignment.Center,
                        ),
                    )
                    .align(Alignment.BottomEnd)
                    .padding(AppDimension.Padding.big),
                onClick = { consume(Action.Click.ActionButton) },
                contentIcon = Icons.Default.Add,
                selectedContentIcon = Icons.Default.Delete,
                selectedMode = state.selectedItems.isNotEmpty(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@SuppressLint("UnusedContentLambdaTargetStateParameter")
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
                date = PropertyHolder.DateProperty.now(),
            )
        }.toList()

        val itemsPaging = {
            flowOf(PagingData.from(items))
        }
        val state = State(
            pagingUiState = itemsPaging,
            query = "",
            selectedItems = persistentSetOf("uuid1", "uuid2"),
            isKeyboardVisible = false,
        )
        AnimatedContent("") {
            SharedTransitionScope { modifier ->
                AllTrainingsScreen(
                    state = state,
                    modifier = modifier,
                    consume = {},
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                )
            }
        }
    }
}
