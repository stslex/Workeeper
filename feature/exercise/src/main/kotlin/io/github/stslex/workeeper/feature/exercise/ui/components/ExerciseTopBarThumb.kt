// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay

/**
 * The editor's `.thumb`, in the pushed bar's trailing slot (§26, "The image moves into the pushed
 * top bar"; extraction §7.7).
 *
 * The kit owns the box; this owns the picture and the two destinations:
 *
 *  - **image present** → the full-screen viewer, where replace and remove live now;
 *  - **image absent** → the picker sheet, which is what the type mark's dashed box promises.
 *
 * One control, two destinations, decided by whether there is anything to look at. The form carries
 * no image row of its own: the thumb, its viewer and the picker are the whole of the affordance.
 */
@Composable
internal fun ExerciseTopBarThumb(
    type: ExerciseTypeUiModel,
    imageDisplay: ImageDisplay,
    onOpenImage: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: Any? = when (imageDisplay) {
        ImageDisplay.None -> null
        is ImageDisplay.FromPath -> "${imageDisplay.path}?v=${imageDisplay.lastModified}"
        is ImageDisplay.FromUri -> imageDisplay.uri
    }
    AppExerciseThumb(
        // The bar's own edge padding is `Space.xxs` (2dp), because `.icon-btn` hangs into the
        // gutter and its GLYPH lands 13.5dp inside a 48dp box — 15.5dp from the screen edge, which
        // is the content edge to within half a dp (`AppTopBar`'s KDoc derives it). The thumb has no
        // such inset: its box edge IS its visual edge, so without this it would sit 2dp off the
        // screen while every other trailing mark sits at 15.5. `Space.md` puts it at 14 — the rung
        // below the icon's optical edge rather than the one above, because the drawn thumb aligns
        // with the gutter and 18 would overshoot it.
        modifier = modifier
            .padding(end = AppDimension.Space.md)
            .testTag("ExerciseEditImageThumb"),
        isWeighted = type == ExerciseTypeUiModel.WEIGHTED,
        onClick = if (model != null) onOpenImage else onPickImage,
        // The control has two destinations, so it needs two labels: TalkBack must say which one
        // this tap will take. An unlabelled 46dp box whose only child is a decorative type mark is
        // a control a screen reader cannot discover, let alone identify.
        contentDescription = stringResource(
            if (model != null) {
                R.string.feature_exercise_image_thumb_open_description
            } else {
                R.string.feature_exercise_image_thumb_pick_description
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
