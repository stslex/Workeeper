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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppConfirmDialog(
    title: String,
    body: String,
    impactSummary: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        AppConfirmDialogContent(
            title = title,
            body = body,
            impactSummary = impactSummary,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = modifier,
            dismissLabel = dismissLabel,
        )
    }
}

/**
 * The dialog's **content**, without the window.
 *
 * `Dialog {}` composes into its own window and Paparazzi models a single one, so a confirm
 * dialog drawn only inside [AppConfirmDialog] has no visual gate at all — the combination of
 * a surface with a drawn treatment and no way to photograph it is the one worth avoiding. The
 * window is the part Paparazzi cannot model; the content is not, and it is where every colour,
 * radius and rung actually lives. Splitting them costs one composable and buys the gate.
 *
 * [AppConfirmDialog] is the only production caller. This exists so goldens can render the same
 * pixels without a window, which is why it must stay a pure function of its arguments.
 */
@Composable
fun AppConfirmDialogContent(
    title: String,
    body: String,
    impactSummary: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    val resolvedDismissLabel = dismissLabel ?: stringResource(R.string.core_ui_kit_action_cancel)
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppUi.shapes.small)
                .background(AppUi.colors.setType.failureBackground)
                .padding(AppDimension.Space.sm),
            text = impactSummary,
            style = AppUi.typography.labelMedium,
            color = AppUi.colors.setType.failureForeground,
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
            AppButton.Primary(
                text = resolvedDismissLabel,
                onClick = onDismiss,
                size = AppButtonSize.MEDIUM,
            )
            AppButton.Destructive(
                text = confirmLabel,
                onClick = onConfirm,
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppConfirmDialogPreview() {
    AppTheme {
        AppConfirmDialog(
            title = "Delete archive?",
            body = "This action cannot be undone.",
            impactSummary = "47 sessions of history will be deleted",
            confirmLabel = "Delete forever",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
