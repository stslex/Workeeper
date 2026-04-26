// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
internal fun ExerciseHero(
    type: ExerciseTypeDataModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1),
        contentAlignment = Alignment.Center,
    ) {
        val isWeighted = type == ExerciseTypeDataModel.WEIGHTED
        Icon(
            modifier = Modifier.size(36.dp),
            imageVector = if (isWeighted) Icons.Filled.FitnessCenter else Icons.Filled.AccessibilityNew,
            contentDescription = null,
            tint = if (isWeighted) {
                AppUi.colors.accentTintedForeground
            } else {
                AppUi.colors.setType.warmupForeground
            },
        )
    }
}
