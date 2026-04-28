// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.feature.exercise.R

@Composable
internal fun PermissionDeniedDialog(
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.feature_exercise_image_permission_denied_title),
        body = stringResource(R.string.feature_exercise_image_permission_denied_body),
        confirmLabel = stringResource(
            R.string.feature_exercise_image_permission_denied_action_settings,
        ),
        // Default dismissLabel falls back to the shared kit "Cancel" string.
        onConfirm = onSettingsClick,
        onDismiss = onDismiss,
    )
}
