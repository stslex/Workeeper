package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action

@Composable
internal fun SingleTrainingsScreen(
    state: TrainingStore.State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {

    }
}