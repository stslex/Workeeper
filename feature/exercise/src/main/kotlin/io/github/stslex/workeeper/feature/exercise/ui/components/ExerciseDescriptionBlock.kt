// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay

/**
 * The `ОПИСАНИЕ` section content — description plus the picture beside it, and the image's only
 * affordance. A null [onDescriptionChange] is read mode (`v3-editors.md` §3.1).
 */
@Composable
internal fun ExerciseDescriptionBlock(
    description: String,
    type: ExerciseTypeUiModel,
    imageDisplay: ImageDisplay,
    modifier: Modifier = Modifier,
    onOpenImage: (() -> Unit)? = null,
    onPickImage: (() -> Unit)? = null,
    onDescriptionChange: ((String) -> Unit)? = null,
) {
    val model: Any? = when (imageDisplay) {
        ImageDisplay.None -> null
        is ImageDisplay.FromPath -> "${imageDisplay.path}?v=${imageDisplay.lastModified}"
        is ImageDisplay.FromUri -> imageDisplay.uri
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ExerciseDescriptionBlock"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (onDescriptionChange == null) {
                Text(
                    modifier = Modifier.testTag("ExerciseDescriptionText"),
                    text = description,
                    style = AppUi.typography.text.body,
                    color = AppUi.colors.textSecondary,
                )
            } else {
                // No explicit height — `.tf.multi` is the field's own number (extraction §7.2).
                AppTextField(
                    modifier = Modifier.testTag("ExerciseEditDescriptionField"),
                    accessibilityLabel = stringResource(R.string.feature_exercise_edit_label_description),
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = stringResource(R.string.feature_exercise_edit_placeholder_description),
                    singleLine = false,
                )
            }
        }
        AppExerciseThumb(
            modifier = Modifier.testTag("ExerciseDescriptionImage"),
            isWeighted = type == ExerciseTypeUiModel.WEIGHTED,
            onClick = if (model != null) onOpenImage else onPickImage,
            // Three states, three labels: open the picture, add one, or say there is none.
            contentDescription = stringResource(
                when {
                    model != null -> R.string.feature_exercise_image_thumb_open_description
                    onPickImage != null -> R.string.feature_exercise_image_thumb_pick_description
                    else -> R.string.feature_exercise_image_placeholder_description
                },
            ),
            content = model?.let {
                {
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(it)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(
                            R.string.feature_exercise_image_thumb_description,
                        ),
                        contentScale = ContentScale.Crop,
                    )
                }
            },
        )
    }
}

@Preview
@Composable
private fun ExerciseDescriptionBlockReadLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseDescriptionBlock(
            description = "Разводи гантели в стороны до уровня плеч, локти чуть согнуты.",
            type = ExerciseTypeUiModel.WEIGHTED,
            imageDisplay = ImageDisplay.None,
        )
    }
}

@Preview
@Composable
private fun ExerciseDescriptionBlockEditDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseDescriptionBlock(
            description = "",
            type = ExerciseTypeUiModel.WEIGHTLESS,
            imageDisplay = ImageDisplay.None,
            onPickImage = {},
            onDescriptionChange = {},
        )
    }
}
