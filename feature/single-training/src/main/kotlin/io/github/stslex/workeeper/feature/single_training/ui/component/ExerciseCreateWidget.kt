package io.github.stslex.workeeper.feature.single_training.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.single_training.R

@Composable
internal fun ExerciseCreateWidget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(
            stringResource(R.string.feature_single_training_field_exercise_create_label),
        )
    }
}

@Composable
@Preview
private fun ExerciseCreateWidgetPreview() {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            ExerciseCreateWidget(
                onClick = {},
            )
        }
    }
}
