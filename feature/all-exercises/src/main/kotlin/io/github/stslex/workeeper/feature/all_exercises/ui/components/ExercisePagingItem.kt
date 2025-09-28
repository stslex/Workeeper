package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.ui.mvi.model.ExerciseUiModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePagingItem(
    item: ExerciseUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = animateColorAsState(
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 600),
    )
    val contentColor = animateColorAsState(
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 600),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(containerColor.value)
            .padding(AppDimension.Padding.big),
    ) {
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

@Composable
@Preview
private fun ExercisePagingItemPreview() {
    AppTheme {
        val item = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "nameOfExercise",
            dateProperty = PropertyHolder.DateProperty.now(),
        )
        ExercisePagingItem(
            item = item,
            isSelected = true,
            onClick = {},
            onLongClick = {},
        )
    }
}
