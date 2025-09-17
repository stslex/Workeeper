package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
internal fun TrainingFloatingActionButton(
    isDeletingMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = if (isDeletingMode) {
                Icons.Default.Delete
            } else {
                Icons.Default.Add
            },
            contentDescription = null
        )
    }
}

@Composable
@Preview()
private fun TrainingFloatingActionButtonPreview() {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        ) {
            TrainingFloatingActionButton(
                onClick = {},
                isDeletingMode = true
            )
        }
    }
}