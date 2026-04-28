// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun ImageSourceDialog(
    onSourceSelected: (ImageSourceUiModel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg)
                .testTag("ExerciseImageSourceDialog"),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(R.string.feature_exercise_image_source_dialog_title),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            SourceOption(
                label = stringResource(R.string.feature_exercise_image_source_camera),
                icon = Icons.Filled.PhotoCamera,
                tag = "ExerciseImageSourceCamera",
                onClick = { onSourceSelected(ImageSourceUiModel.Camera) },
            )
            SourceOption(
                label = stringResource(R.string.feature_exercise_image_source_gallery),
                icon = Icons.Filled.PhotoLibrary,
                tag = "ExerciseImageSourceGallery",
                onClick = { onSourceSelected(ImageSourceUiModel.Gallery) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppButton.Tertiary(
                    text = stringResource(KitR.string.core_ui_kit_action_cancel),
                    onClick = onDismiss,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Composable
private fun SourceOption(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimension.Space.sm,
                vertical = AppDimension.Space.md,
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = icon,
            contentDescription = null,
            tint = AppUi.colors.textPrimary,
        )
        Text(
            text = label,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
    }
}
