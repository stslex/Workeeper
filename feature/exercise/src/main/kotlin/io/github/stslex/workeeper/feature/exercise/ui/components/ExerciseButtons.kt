package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
internal fun ExerciseButtonsRow(
    isDeleteVisible: Boolean,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        ConfirmationButton(Icons.Default.Check, onConfirmClick)
        Spacer(Modifier.weight(1f))
        if (isDeleteVisible) {
            ConfirmationButton(Icons.Default.Delete, onDeleteClick)
        }
        Spacer(Modifier.weight(1f))
        ConfirmationButton(Icons.Default.Clear, onCancelClick)
    }
}

@Composable
private fun ConfirmationButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
        )
    }
}

@Composable
@Preview(device = "id:pixel_4", showSystemUi = true, showBackground = true)
private fun ExerciseButtonsRowPreview() {
    AppTheme {
        Box(
            modifier = Modifier.padding(AppDimension.Padding.large + AppDimension.Padding.large),
        ) {
            ExerciseButtonsRow(
                isDeleteVisible = true,
                onConfirmClick = {},
                onCancelClick = {},
                onDeleteClick = {},
            )
        }
    }
}
