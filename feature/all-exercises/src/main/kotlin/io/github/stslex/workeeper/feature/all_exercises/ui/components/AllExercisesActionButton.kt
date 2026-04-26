package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.fab.AppFAB
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_exercises.R

@Composable
internal fun AllExercisesActionButton(
    selectedMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val icon = if (selectedMode) Icons.Outlined.Delete else Icons.Outlined.Create
    val descriptionRes = if (selectedMode) {
        R.string.feature_all_action_btn_description_delete
    } else {
        R.string.feature_all_action_btn_description_create
    }
    AppFAB(
        modifier = modifier,
        icon = icon,
        contentDescription = stringResource(descriptionRes),
        onClick = onClick,
    )
}

@Composable
@Preview
private fun AllExercisesActionButtonPreview() {
    AppTheme {
        AllExercisesActionButton(selectedMode = false) { }
    }
}
