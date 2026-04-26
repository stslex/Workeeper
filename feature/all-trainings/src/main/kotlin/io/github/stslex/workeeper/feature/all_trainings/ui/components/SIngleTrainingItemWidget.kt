package io.github.stslex.workeeper.feature.all_trainings.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.model.ItemPosition
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingUiModel
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Suppress("LongParameterList", "UnusedParameter")
internal fun SingleTrainingItemWidget(
    item: TrainingUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    isSelected: Boolean,
    itemPosition: ItemPosition,
    modifier: Modifier = Modifier,
) {
    val supporting = if (item.labels.isEmpty()) null else item.labels.joinToString(separator = " · ")
    AppListItem(
        modifier = modifier,
        headline = item.name,
        supportingText = supporting,
        onClick = onClick,
    )
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
private fun SingleTrainingItemWidgetPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            var isSelected by remember { mutableStateOf(false) }
            val labels = Array(20) { "label$it" }.toImmutableList()
            val uuids = Array(20) { "uuid$it" }.toImmutableList()
            AnimatedContent("") {
                SharedTransitionScope { modifier ->
                    SingleTrainingItemWidget(
                        modifier = modifier,
                        animatedContentScope = this@AnimatedContent,
                        item = TrainingUiModel(
                            uuid = "uuid",
                            name = "training item with long long long name very very long name",
                            labels = labels,
                            exerciseUuids = uuids,
                            date = PropertyHolder.DateProperty.now(),
                        ),
                        isSelected = isSelected,
                        onClick = { isSelected = !isSelected },
                        onLongClick = {},
                        sharedTransitionScope = this,
                        itemPosition = ItemPosition.MIDDLE,
                    )
                }
            }
        }
    }
}
