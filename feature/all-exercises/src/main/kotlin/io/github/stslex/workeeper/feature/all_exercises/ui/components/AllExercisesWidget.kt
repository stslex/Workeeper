package io.github.stslex.workeeper.feature.all_exercises.ui.components

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.State
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Suppress("UnusedParameter")
internal fun AllExercisesWidget(
    state: State,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val items = remember { state.items.invoke() }.collectAsLazyPagingItems()
    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        AppTextField(
            modifier = Modifier
                .padding(AppDimension.Padding.big)
                .testTag("AllExercisesSearchWidget"),
            value = state.query,
            onValueChange = { consume(Action.Input.SearchQuery(it)) },
            label = "Search",
            leadingIcon = Icons.Default.Search,
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
                    .clip(MaterialTheme.shapes.large)
                    .testTag("AllExercisesList"),
                state = lazyState,
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.uuid },
                ) { index ->
                    items[index]?.let { item ->
                        AppListItem(
                            modifier = Modifier.testTag("ExerciseItem_${item.uuid}"),
                            headline = item.name,
                            supportingText = item.dateProperty.uiValue,
                            onClick = { consume(Action.Click.Item(item.uuid)) },
                        )
                    }
                }
            }

            @Suppress("ComplexCondition")
            if (
                items.loadState.refresh is LoadState.NotLoading &&
                items.loadState.append is LoadState.NotLoading &&
                items.loadState.prepend is LoadState.NotLoading &&
                items.itemCount == 0
            ) {
                EmptyWidget(
                    query = state.query,
                    modifier = Modifier.testTag("AllExercisesEmptyState"),
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
        val items = Array(5) { index ->
            ExerciseUiModel(
                uuid = Uuid.random().toString(),
                name = "nameOfExercise$index",
                dateProperty = PropertyHolder.DateProperty.now(),
            )
        }.toList()
        val itemsPaging = {
            flowOf(PagingData.from(items))
        }
        val state = State(
            items = itemsPaging,
            selectedItems = persistentSetOf(),
            query = "",
            isKeyboardVisible = false,
        )
        AnimatedContent("") {
            SharedTransitionScope {
                AllExercisesWidget(
                    state = state,
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    consume = {},
                    lazyState = rememberLazyListState(),
                    modifier = it,
                )
            }
        }
    }
}
