package io.github.stslex.workeeper.feature.all_trainings.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ContextualFlowRowOverflow.Companion.expandOrCollapseIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.paging_item.BasePagingColumnItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.model.ItemPosition
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Suppress("Deprecation")
@Composable
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
    BasePagingColumnItem(
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        itemKey = item.uuid,
        onClick = onClick,
        onLongClick = onLongClick,
        isSelected = isSelected,
        itemPosition = itemPosition,
    ) { contentColorState ->
        Text(
            text = item.name,
            style = MaterialTheme.typography.displayMedium,
            maxLines = 3,
            color = contentColorState.value,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.labels.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimension.Padding.medium),
            )
            ContextualFlowRow(
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                itemCount = item.labels.size,
                overflow = expandOrCollapseIndicator(
                    expandIndicator = {
                        val hidden = abs(totalItemCount - shownItemCount)
                        SingleTrainingLabel(
                            label = "+$hidden",
                        )
                    },
                    collapseIndicator = {},
                ),
            ) {
                item.labels.forEachIndexed { index, label ->
                    SingleTrainingLabel(
                        label = label,
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleTrainingLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier,
    ) {
        OutlinedCard(
            modifier = modifier
                .padding(AppDimension.Padding.small),
        ) {
            Text(
                modifier = Modifier.padding(
                    horizontal = AppDimension.Padding.medium,
                ),
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
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
            val labels = Array(20) {
                "label$it"
            }.toImmutableList()
            val uuids = Array(20) {
                "uuid$it"
            }.toImmutableList()
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
                        onClick = {
                            isSelected = !isSelected
                        },
                        onLongClick = {},
                        sharedTransitionScope = this,
                        itemPosition = ItemPosition.MIDDLE,
                    )
                }
            }
        }
    }
}
