// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise.resources.Res
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_source_camera
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_source_dialog_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_image_source_gallery
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageSourceUiModel
import org.jetbrains.compose.resources.stringResource

/**
 * «Камера» · «Галерея» — the picker the empty thumb opens (extraction §7.4). A menu sheet, not a
 * confirmation; content-only, hosted by `AppBottomSheet` so the layout stays photographable.
 */
@Composable
internal fun ImageSourceSheetContent(
    onSourceSelected: (ImageSourceUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSheetLayout(
        modifier = modifier.testTag("ExerciseImageSourceSheet"),
        title = stringResource(Res.string.feature_exercise_image_source_dialog_title),
    ) {
        AppSheetItem(
            modifier = Modifier.testTag("ExerciseImageSourceCamera"),
            title = stringResource(Res.string.feature_exercise_image_source_camera),
            onClick = { onSourceSelected(ImageSourceUiModel.Camera) },
        )
        AppSheetItem(
            modifier = Modifier.testTag("ExerciseImageSourceGallery"),
            title = stringResource(Res.string.feature_exercise_image_source_gallery),
            onClick = { onSourceSelected(ImageSourceUiModel.Gallery) },
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ImageSourceSheetContentLightPreview() {
    ImageSourceSheetContentPreview(themeMode = ThemeMode.LIGHT)
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ImageSourceSheetContentDarkPreview() {
    ImageSourceSheetContentPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun ImageSourceSheetContentPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
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
