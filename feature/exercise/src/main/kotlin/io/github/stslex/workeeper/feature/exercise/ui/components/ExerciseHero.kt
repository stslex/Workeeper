// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageDisplay

@Composable
internal fun ExerciseHero(
    type: ExerciseTypeUiModel,
    imageDisplay: ImageDisplay,
    modifier: Modifier = Modifier,
    onImageClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (imageDisplay !is ImageDisplay.None && onImageClick != null) {
        Modifier.clickable(onClick = onImageClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .then(clickableModifier)
            .testTag("ExerciseHero"),
        contentAlignment = Alignment.Center,
    ) {
        when (imageDisplay) {
            ImageDisplay.None -> ExerciseTypePlaceholder(type = type)
            is ImageDisplay.FromPath -> AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ExerciseHeroImage"),
                model = ImageRequest.Builder(LocalContext.current)
                    // Cache-bust by mtime so a replaced file at the same path is not served
                    // from Coil's URI-keyed cache.
                    .data("${imageDisplay.path}?v=${imageDisplay.lastModified}")
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.feature_exercise_image_thumb_description),
                contentScale = ContentScale.Crop,
            )

            is ImageDisplay.FromUri -> AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ExerciseHeroImage"),
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
private fun ExerciseTypePlaceholder(
    type: ExerciseTypeUiModel,
) {
    val isWeighted = type == ExerciseTypeUiModel.WEIGHTED
    Icon(
        modifier = Modifier.size(36.dp),
        imageVector = if (isWeighted) Icons.Filled.FitnessCenter else Icons.Filled.AccessibilityNew,
        contentDescription = stringResource(R.string.feature_exercise_image_placeholder_description),
        tint = if (isWeighted) {
            AppUi.colors.accentTintedForeground
        } else {
            AppUi.colors.setType.warmupForeground
        },
    )
}
