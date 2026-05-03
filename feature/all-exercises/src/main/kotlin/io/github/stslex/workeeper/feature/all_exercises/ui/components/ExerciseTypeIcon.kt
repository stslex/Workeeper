// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseTypeUiModel

@Composable
internal fun ExerciseTypeIcon(
    type: ExerciseTypeUiModel,
    modifier: Modifier = Modifier,
) {
    val isWeighted = type == ExerciseTypeUiModel.WEIGHTED
    val tint = if (isWeighted) {
        AppUi.colors.accentTintedForeground
    } else {
        AppUi.colors.setType.warmupForeground
    }
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(AppUi.shapes.small)
            .background(AppUi.colors.surfaceTier4),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = if (isWeighted) {
                Icons.Filled.FitnessCenter
            } else {
                Icons.Filled.AccessibilityNew
            },
            contentDescription = null,
            tint = tint,
        )
    }
}
