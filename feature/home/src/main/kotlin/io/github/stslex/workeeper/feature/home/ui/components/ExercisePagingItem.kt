package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePagingItem(
    item: ExerciseUiModel,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        onClick = { /*todo do smth*/ }
    ) {
        Column(
            modifier = Modifier
                .padding(AppDimension.Padding.medium)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = item.reps.toString(),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = item.sets.toString(),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = item.weight.toString(),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = item.timestamp.toString(),
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
            sets = 12,
            reps = 13,
            weight = 60,
            timestamp = System.currentTimeMillis()
        )
        ExercisePagingItem(item)
    }
}