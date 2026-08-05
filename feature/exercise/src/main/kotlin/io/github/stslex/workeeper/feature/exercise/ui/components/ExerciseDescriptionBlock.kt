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
 * The `ОПИСАНИЕ` section's content — the description and the picture beside it
 * (`v3-editors.md` §3.1, §3.2; D-OPEN-3, D-OPEN-9).
 *
 * ## One component, two hosts
 *
 * The read screen draws it read-only and the editor draws it editable, and they are the **same
 * block**: D-OPEN-3 put the image entry point beside the description on both, and a second copy
 * per screen is the drift this arc exists to remove. The section head is the host's, because the
 * two hosts sit in different rhythms — a scrolling read frame and a form.
 *
 * ## Why the picture is here at all
 *
 * It used to be two things: `ExerciseTopBarThumb` on the editor and a full-width hero on read.
 * ED6 deleted the first and D-OPEN-9 replaced the second, so this block carries the whole
 * affordance now — including the placeholder, which is [AppExerciseThumb]'s own type mark and
 * therefore never needed a home in this feature. **The placement is the statement**: the image is
 * optional and descriptive, so it sits with the other optional descriptive thing.
 *
 * ## The two callbacks encode D-OPEN-3's rule once
 *
 * With a picture the tap opens the viewer; without one it opens the picker. Either may be absent
 * — read mode has no picker — and when the one this state resolves to is absent, the box is drawn
 * and inert rather than promising an action it cannot take.
 *
 * [onDescriptionChange] is the mode: **the null is the exclusion**, the same grammar
 * `PlanEditorBody` and `PlanSetCard` already use.
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
                // No explicit height — `.tf.multi` is the same box taller and the FIELD owns that
                // number (extraction §7.2). A call site that sets its own guesses at a value the
                // drawing already puts at 96.
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
            // Three states, three labels: open the picture, add one, or say there is none. A box
            // whose only child is a decorative type mark tells a screen reader nothing by itself.
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
