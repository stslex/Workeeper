package io.github.stslex.workeeper.dialog.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.toDp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseDialogWidget(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogHeight = LocalView.current.height.toDp * 0.5f
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .height(dialogHeight)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(AppDimension.Radius.large)
            )
            .padding(AppDimension.Padding.large),
        properties = DialogProperties()
    ) {
        Column {
            Text(
                text = "create new exercise",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
@Preview
private fun ExerciseDialogWidgetPreview() {
    AppTheme {
        ExerciseDialogWidget(
            onDismiss = {}
        )
    }
}