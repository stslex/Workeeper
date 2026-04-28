// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageDisplay

private val THUMB_SIZE = 72.dp

@Composable
internal fun ImageEditRow(
    type: ExerciseTypeUiModel,
    imageDisplay: ImageDisplay,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImage = imageDisplay !is ImageDisplay.None
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ExerciseEditImageRow"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImageThumb(
            type = type,
            imageDisplay = imageDisplay,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            AppButton.Secondary(
                modifier = Modifier.testTag("ExerciseEditImageEditButton"),
                text = stringResource(
                    if (hasImage) {
                        R.string.feature_exercise_image_action_edit
                    } else {
                        R.string.feature_exercise_image_action_add
                    },
                ),
                onClick = onEditClick,
                size = AppButtonSize.SMALL,
            )
            if (hasImage) {
                AppButton.Tertiary(
                    modifier = Modifier.testTag("ExerciseEditImageRemoveButton"),
                    text = stringResource(R.string.feature_exercise_image_action_remove),
                    onClick = onRemoveClick,
                    size = AppButtonSize.SMALL,
                )
            }
        }
    }
}

@Composable
private fun ImageThumb(
    type: ExerciseTypeUiModel,
    imageDisplay: ImageDisplay,
) {
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .testTag("ExerciseEditImageThumb"),
        contentAlignment = Alignment.Center,
    ) {
        when (imageDisplay) {
            ImageDisplay.None -> ThumbPlaceholder(type)
            is ImageDisplay.FromPath -> AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = ImageRequest.Builder(LocalContext.current)
                    .data("${imageDisplay.path}?v=${imageDisplay.lastModified}")
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.feature_exercise_image_thumb_description),
                contentScale = ContentScale.Crop,
            )

            is ImageDisplay.FromUri -> AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageDisplay.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.feature_exercise_image_thumb_description),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ThumbPlaceholder(type: ExerciseTypeUiModel) {
    val isWeighted = type == ExerciseTypeUiModel.WEIGHTED
    Icon(
        modifier = Modifier.size(28.dp),
        imageVector = if (isWeighted) Icons.Filled.FitnessCenter else Icons.Filled.AccessibilityNew,
        contentDescription = stringResource(R.string.feature_exercise_image_placeholder_description),
        tint = if (isWeighted) {
            AppUi.colors.accentTintedForeground
        } else {
            AppUi.colors.setType.warmupForeground
        },
    )
}
