package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
internal fun HomeActionButton(
    selectedMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        AnimatedContent(selectedMode) {
            Icon(
                imageVector = if (it) {
                    Icons.Default.Delete
                } else {
                    Icons.Default.Add
                },
                contentDescription = null
            )
        }

    }
}

@Composable
@Preview
private fun HomeActionButtonPreview() {
    AppTheme {
        HomeActionButton(
            selectedMode = false
        ) { }
    }
}