package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.R

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
        AnimatedContent(selectedMode) { isSelecting ->
            val descriptionRes = if (isSelecting) {
                R.string.home_action_button_delete_description
            } else {
                R.string.home_action_button_add_description
            }
            Icon(
                imageVector = if (isSelecting) {
                    Icons.Default.Delete
                } else {
                    Icons.Default.Add
                },
                contentDescription = stringResource(descriptionRes)
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