// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.feature.all_exercises.R

@Composable
internal fun ExercisesEmptyState(
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        modifier = modifier.testTag("AllExercisesEmptyState"),
        headline = stringResource(R.string.feature_all_exercises_empty_headline),
        supportingText = stringResource(R.string.feature_all_exercises_empty_supporting),
        icon = Icons.Filled.FitnessCenter,
    )
}
