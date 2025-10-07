package io.github.stslex.workeeper.feature.exercise.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel

@Composable
internal fun ExerciseSetsField(
    property: SetsUiModel,
    onClick: (SetsUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(property.type.color)
    ElevatedCard(
        modifier = modifier
            .padding(AppDimension.Padding.medium)
            .fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor.copy(
                alpha = 0.5f,
            ),
            contentColor = containerColor,
        ),
        onClick = {
            onClick(property)
        },
    ) {
        Row(
            modifier = Modifier
                .padding(AppDimension.Padding.medium),
        ) {
            ExerciseTextField(
                modifier = Modifier.weight(1f),
                text = property.reps.uiValue,
                label = stringResource(R.string.feature_exercise_field_label_reps),
            )
            Spacer(Modifier.width(AppDimension.Padding.small))
            ExerciseTextField(
                modifier = Modifier
                    .weight(1f),
                text = property.weight.uiValue,
                label = stringResource(R.string.feature_exercise_field_label_weight),
            )
            Spacer(Modifier.width(AppDimension.Padding.small))
            ExerciseTextField(
                modifier = Modifier.weight(1f),
                text = stringResource(property.type.stringRes),
                label = stringResource(R.string.feature_exercise_field_label_set_type),
            )
        }
    }
}

@Composable
private fun ExerciseTextField(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = AppDimension.Padding.medium)
                .fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.align(Alignment.TopStart),
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
private fun ExerciseSetsFieldPreview() {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            ExerciseSetsField(
                property = SetsUiModel(
                    uuid = "uuid",
                    reps = PropertyHolder.IntProperty.new(10),
                    weight = PropertyHolder.DoubleProperty.new(14.50),
                    type = SetUiType.WARM,
                ),
                onClick = {},
            )
        }
    }
}
