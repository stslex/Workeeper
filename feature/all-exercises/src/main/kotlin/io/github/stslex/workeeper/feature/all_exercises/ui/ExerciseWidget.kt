package io.github.stslex.workeeper.feature.all_exercises.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.PagingData
import dev.chrisbanes.haze.rememberHazeState
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi.uiFeatures
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.ui.components.AllExercisesActionButton
import io.github.stslex.workeeper.feature.all_exercises.ui.components.AllExercisesWidget
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ExerciseWidget(
    state: State,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    consume: (Action) -> Unit,
    lazyState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState(blurEnabled = uiFeatures.enableBlur)
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("AllExercisesScreen"),
    ) {
        AllExercisesWidget(
            modifier = Modifier
                .fillMaxSize()
                .testTag("AllExercisesWidget"),
            state = state,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            consume = consume,
            hazeState = hazeState,
            lazyState = lazyState,
        )
        with(sharedTransitionScope) {
            AllExercisesActionButton(
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
                    .padding(AppDimension.Padding.big)
                    .testTag("AllExercisesActionButton"),
                hazeState = hazeState,
                selectedMode = state.selectedItems.isNotEmpty(),
            ) {
                consume(Action.Click.FloatButtonClick)
            }
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
private fun ExerciseWidgetPreview() {
    AppTheme {
        val items = Array(10) { index ->
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
                ExerciseWidget(
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
