package io.github.stslex.workeeper.feature.all_exercises.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.paging_item.BasePagingColumnItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.model.ItemPosition
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ExercisePagingItem(
    item: ExerciseUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    isSelected: Boolean,
    itemPosition: ItemPosition,
    modifier: Modifier = Modifier,
) {
    BasePagingColumnItem(
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        sharedContentState = sharedTransitionScope.rememberSharedContentState(item.uuid),
        onClick = onClick,
        onLongClick = onLongClick,
        isSelected = isSelected,
        itemPosition = itemPosition,
    ) { contentColor ->
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLarge,
            color = contentColor.value,
        )
        Text(
            text = item.dateProperty.uiValue,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.value,
        )
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
private fun ExercisePagingItemPreview() {
    AppTheme {
        val item = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "nameOfExercise",
            dateProperty = PropertyHolder.DateProperty.now(),
        )
        AnimatedContent("") {
            SharedTransitionScope { modifier ->
                ExercisePagingItem(
                    modifier = modifier,
                    item = item,
                    isSelected = true,
                    onClick = {},
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    itemPosition = ItemPosition.SINGLE,
                    onLongClick = {},
                )
            }
        }
    }
}
