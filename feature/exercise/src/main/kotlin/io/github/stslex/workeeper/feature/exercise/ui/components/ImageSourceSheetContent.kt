// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageSourceUiModel

/**
 * «Камера» · «Галерея» — the picker the empty thumb opens (§26, "Every modal on the three editors
 * is a SHEET"; extraction §7.4).
 *
 * A **menu** sheet, not a confirmation: two choices and no question, which is `#sh-pick`'s shape
 * and why `AppConfirmSheet` is the wrong component for it.
 *
 * Two things went with the dialog it replaces, and both are subtractions rather than losses.
 * **The two Material photo glyphs** (`PhotoCamera`, `PhotoLibrary`) — the kit ships neither, and
 * drawing them here would settle two of B33(b)'s open glyph questions by writing them; `#sh-pick`
 * draws its items as text and so does this. **The cancel button** — a sheet's scrim and its drag
 * ARE the dismiss, so a third row that only closed the sheet was a button for a gesture.
 *
 * Content-only, hosted by `AppBottomSheet` at the call site: `ModalBottomSheet` composes into its
 * own window and Paparazzi models one, so the layout is separated to keep it photographable.
 */
@Composable
internal fun ImageSourceSheetContent(
    onSourceSelected: (ImageSourceUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSheetLayout(
        modifier = modifier.testTag("ExerciseImageSourceSheet"),
        title = stringResource(R.string.feature_exercise_image_source_dialog_title),
    ) {
        AppSheetItem(
            modifier = Modifier.testTag("ExerciseImageSourceCamera"),
            title = stringResource(R.string.feature_exercise_image_source_camera),
            onClick = { onSourceSelected(ImageSourceUiModel.Camera) },
        )
        AppSheetItem(
            modifier = Modifier.testTag("ExerciseImageSourceGallery"),
            title = stringResource(R.string.feature_exercise_image_source_gallery),
            onClick = { onSourceSelected(ImageSourceUiModel.Gallery) },
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageSourceSheetContentPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppUi.colors.surfaceTier3)
                .padding(AppDimension.screenEdge),
        ) {
            ImageSourceSheetContent(onSourceSelected = {})
        }
    }
}
