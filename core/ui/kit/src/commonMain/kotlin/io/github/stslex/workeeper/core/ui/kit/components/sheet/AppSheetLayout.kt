// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionDivider
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionRow
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_sheet_close
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES
import org.jetbrains.compose.resources.stringResource

/**
 * A bottom sheet's contents: title, then content. Split from [AppBottomSheet] so the layout
 * renders in the main window and can have goldens; scrim and grab handle belong to the window.
 *
 * GUARD: never draw a grab handle here — `ModalBottomSheet` supplies one by default.
 */
@Composable
fun AppSheetLayout(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = AppDimension.Space.sm,
                bottom = AppDimension.Space.xl,
            ),
    ) {
        title?.let { text ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimension.screenEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = text,
                    style = AppUi.typography.text.section,
                    color = AppUi.colors.textPrimary,
                )
                onClose?.let { close ->
                    IconButton(onClick = close) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconSm),
                            imageVector = Icons.Default.Close,
                            // Labelled, not decorative: this is the only close affordance.
                            contentDescription = stringResource(Res.string.core_ui_kit_sheet_close),
                            tint = AppUi.colors.textTertiary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppDimension.Space.md))
        }
        content()
    }
}

/** Form (a): a menu. Rows, hairlines between them, nothing below the last one. */
@Composable
fun AppSheetMenuContent(
    items: List<AppSheetMenuItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) AppSectionDivider()
            AppSectionRow(
                title = item.title,
                supporting = item.supporting,
                onClick = item.onClick,
            )
        }
    }
}

/** One row of [AppSheetMenuContent]. */
data class AppSheetMenuItem(
    val title: String,
    val supporting: String? = null,
    val onClick: () -> Unit,
)

/**
 * Form (b): a confirmation. An explanation, then the actions stacked, confirm above dismiss.
 * [confirmDestructive] is passed rather than inferred.
 */
@Composable
fun AppSheetConfirmContent(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmDestructive: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Text(
            text = message,
            style = AppUi.typography.text.body,
            color = AppUi.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(AppDimension.Space.xs))
        if (confirmDestructive) {
            AppButton.Destructive(
                modifier = Modifier.fillMaxWidth(),
                text = confirmLabel,
                onClick = onConfirm,
                size = AppButtonSize.MEDIUM,
            )
        } else {
            AppButton.Primary(
                modifier = Modifier.fillMaxWidth(),
                text = confirmLabel,
                onClick = onConfirm,
                size = AppButtonSize.MEDIUM,
            )
        }
        dismissLabel?.let { label ->
            onDismiss?.let { handler ->
                AppButton.Tertiary(
                    modifier = Modifier.fillMaxWidth(),
                    text = label,
                    onClick = handler,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSheetLayoutPreview() {
    AppTheme {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier1)) {
            AppSheetLayout(title = "Set type") {
                AppSheetMenuContent(
                    items = listOf(
                        AppSheetMenuItem(title = "Warm-up", onClick = {}),
                        AppSheetMenuItem(title = "Working set", onClick = {}),
                        AppSheetMenuItem(title = "To failure", onClick = {}),
                    ),
                )
            }
        }
    }
}
