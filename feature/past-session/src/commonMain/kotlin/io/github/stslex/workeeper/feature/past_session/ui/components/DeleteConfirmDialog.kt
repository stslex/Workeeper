// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.past_session.resources.Res
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_action_delete
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_delete_dialog_body
import io.github.stslex.workeeper.feature.past_session.resources.feature_past_session_delete_dialog_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = stringResource(Res.string.feature_past_session_delete_dialog_title),
        body = stringResource(Res.string.feature_past_session_delete_dialog_body),
        confirmLabel = stringResource(Res.string.feature_past_session_action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

@Preview(name = "Light")
@Composable
private fun DeleteConfirmDialogLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        DeleteConfirmDialog(onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "Dark")
@Composable
private fun DeleteConfirmDialogDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        DeleteConfirmDialog(onConfirm = {}, onDismiss = {})
    }
}
