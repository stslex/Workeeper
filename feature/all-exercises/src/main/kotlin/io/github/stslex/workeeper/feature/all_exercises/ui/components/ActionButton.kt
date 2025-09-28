package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.buttons.AppActionButton
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.R

@Composable
internal fun HomeActionButton(
    selectedMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppActionButton(
        onClick = onClick,
        modifier = modifier,
        selectedMode = selectedMode,
        contentIcon = Icons.Outlined.Create,
        contentDescription = stringResource(R.string.feature_all_action_btn_description_create),
        selectedContentIcon = Icons.Outlined.Delete,
        selectedContentDescription = stringResource(R.string.feature_all_action_btn_description_delete),
    )
}

@Composable
@Preview
private fun HomeActionButtonPreview() {
    AppTheme {
        HomeActionButton(
            selectedMode = false,
        ) { }
    }
}
