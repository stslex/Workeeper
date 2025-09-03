package io.github.stslex.workeeper.dialog.exercise.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.dialog.exercise.ui.mvi.ExerciseDialogComponent

@Composable
fun ExerciseDialog(
    modifier: Modifier,
    component: ExerciseDialogComponent
) {
    ExerciseDialogWidget(
        onDismiss = {
            component.dismiss()
        },
        modifier = modifier
    )
}
