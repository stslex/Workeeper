// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R

@Composable
internal fun TypeToggle(
    selected: ExerciseTypeDataModel,
    onSelect: (ExerciseTypeDataModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag("ExerciseTypeToggle"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        TypeOption(
            label = stringResource(R.string.feature_exercise_detail_type_weighted),
            isSelected = selected == ExerciseTypeDataModel.WEIGHTED,
            onClick = { onSelect(ExerciseTypeDataModel.WEIGHTED) },
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseTypeOption_WEIGHTED"),
        )
        TypeOption(
            label = stringResource(R.string.feature_exercise_detail_type_weightless),
            isSelected = selected == ExerciseTypeDataModel.WEIGHTLESS,
            onClick = { onSelect(ExerciseTypeDataModel.WEIGHTLESS) },
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseTypeOption_WEIGHTLESS"),
        )
    }
}

@Composable
private fun TypeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) AppUi.colors.accent else AppUi.colors.borderDefault
    val background = if (isSelected) AppUi.colors.accentTintedBackground else AppUi.colors.surfaceTier1
    val textColor = if (isSelected) AppUi.colors.accentTintedForeground else AppUi.colors.textPrimary
    Box(
        modifier = modifier
            .height(AppDimension.heightMd)
            .clip(AppUi.shapes.medium)
            .background(background)
            .border(
                width = AppDimension.borderHairline,
                color = borderColor,
                shape = AppUi.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimension.Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppUi.typography.labelLarge,
            color = textColor,
        )
    }
}
