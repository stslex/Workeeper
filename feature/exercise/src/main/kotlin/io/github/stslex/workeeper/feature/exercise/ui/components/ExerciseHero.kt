// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay

@Composable
internal fun ExerciseHero(
    type: ExerciseTypeUiModel,
    imageDisplay: ImageDisplay,
    modifier: Modifier = Modifier,
    onImageClick: (() -> Unit)? = null,
) {
    val isClickable = imageDisplay !is ImageDisplay.None && onImageClick != null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .clickable(enabled = isClickable) { onImageClick?.invoke() }
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
        // §26 "The image moves into the pushed top bar": the two type marks are kit strokes, and
        // no site that draws a TYPE imports the filled Material pair. One vector per mark, so the
        // hero and the thumb cannot drift.
        imageVector = if (isWeighted) AppIcons.ExerciseWeighted else AppIcons.ExerciseWeightless,
        contentDescription = stringResource(R.string.feature_exercise_image_placeholder_description),
        tint = if (isWeighted) {
            AppUi.colors.accentTintedForeground
        } else {
            AppUi.colors.setType.warmupForeground
        },
    )
}

@Preview
@Composable
private fun ExerciseHeroWeightedPlaceholderLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseHero(
            type = ExerciseTypeUiModel.WEIGHTED,
            imageDisplay = ImageDisplay.None,
        )
    }
}

@Preview
@Composable
private fun ExerciseHeroWeightlessPlaceholderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseHero(
            type = ExerciseTypeUiModel.WEIGHTLESS,
            imageDisplay = ImageDisplay.None,
        )
    }
}
