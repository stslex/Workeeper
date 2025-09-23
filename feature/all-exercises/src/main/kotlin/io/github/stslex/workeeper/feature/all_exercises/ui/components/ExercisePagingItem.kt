package io.github.stslex.workeeper.feature.all_exercises.ui.components

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
import io.github.stslex.workeeper.core.ui.kit.components.CardWithAnimatedBorder
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
    modifier: Modifier = Modifier
) {
    CardWithAnimatedBorder(
        modifier = modifier
            .padding(AppDimension.Padding.medium)
            .fillMaxWidth()
            .wrapContentHeight(),
        onClick = onClick,
        onLongClick = onLongClick,
        isAnimated = isSelected,
        borderSize = AppDimension.Border.medium
    ) {
        Column(
            modifier = Modifier
                .padding(AppDimension.Padding.big)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelLarge
            )
//            Text(
//                text = item.reps.toString(),
//                style = MaterialTheme.typography.bodyLarge
//            )
//            Text(
//                text = item.sets.toString(),
//                style = MaterialTheme.typography.bodyLarge
//            )
//            Text(
//                text = item.weight.toString(),
//                style = MaterialTheme.typography.bodyLarge
//            )
            Text(
                text = item.dateProperty.uiValue,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
@Preview
private fun ExercisePagingItemPreview() {
    AppTheme {
        val item = ExerciseUiModel(
            uuid = Uuid.random().toString(),
            name = "nameOfExercise",
            dateProperty = PropertyHolder.DateProperty()
        )
        ExercisePagingItem(
            item = item,
            isSelected = true,
            onClick = {},
            onLongClick = {}
        )
    }
}