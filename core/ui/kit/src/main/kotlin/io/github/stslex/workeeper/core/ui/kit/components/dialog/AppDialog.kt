package io.github.stslex.workeeper.core.ui.kit.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = { onDismiss?.invoke() }) {
        Column(
            modifier = modifier
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = title,
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = body,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = AppDimension.Space.sm,
                    alignment = Alignment.End,
                ),
            ) {
                if (dismissLabel != null && onDismiss != null) {
                    AppButton.Tertiary(
                        text = dismissLabel,
                        onClick = onDismiss,
                        size = AppButtonSize.MEDIUM,
                    )
                }
                if (destructive) {
                    AppButton.Destructive(
                        text = confirmLabel,
                        onClick = onConfirm,
                        size = AppButtonSize.MEDIUM,
                    )
                } else {
                    AppButton.Primary(
                        text = confirmLabel,
                        onClick = onConfirm,
                        size = AppButtonSize.MEDIUM,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppDialogPreview() {
    AppTheme {
        AppDialog(
            title = "Discard changes?",
            body = "Your unsaved edits will be lost.",
            confirmLabel = "Discard",
            onConfirm = {},
            dismissLabel = "Cancel",
            onDismiss = {},
        )
    }
}
