// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 confirmation sheet: the drawing has no dialog primitive, so every editor modal takes
 * this form. See documentation/feature-specs/screen-extraction.md §7.4.
 */
@Composable
fun AppConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
    confirmDestructive: Boolean = false,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        AppConfirmSheetContent(
            modifier = modifier,
            title = title,
            body = body,
            emphasis = emphasis,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmDestructive = confirmDestructive,
        )
    }
}

/** [AppConfirmSheet] without the window. Keep it a pure function of its arguments. */
@Composable
fun AppConfirmSheetContent(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
    confirmDestructive: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            // `.sheet h3` — 19px/600 → the section rung, which already carries the weight.
            modifier = Modifier.padding(horizontal = AppDimension.Space.xs),
            text = title,
            style = AppUi.typography.text.section,
            color = AppUi.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimension.Space.sm))
        Text(
            // `.sheet .desc` — 15px on `--body`.
            modifier = Modifier.padding(horizontal = AppDimension.Space.xs),
            text = body,
            style = AppUi.typography.text.body,
            color = AppUi.colors.textSecondary,
        )
        emphasis?.let { line ->
            Spacer(modifier = Modifier.height(AppDimension.Space.sm))
            Text(
                modifier = Modifier.padding(horizontal = AppDimension.Space.xs),
                text = line,
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(AppDimension.Space.md))
        AppSheetItem(
            modifier = Modifier.testTag(CONFIRM_TAG),
            title = confirmLabel,
            onClick = onConfirm,
            destructive = confirmDestructive,
        )
        AppSheetItem(
            modifier = Modifier.testTag(DISMISS_TAG),
            title = dismissLabel,
            onClick = onDismiss,
        )
    }
}

/** Stable across every caller, so a UI test does not need to know which sheet it is looking at. */
const val CONFIRM_TAG: String = "AppConfirmSheetConfirm"

/** See [CONFIRM_TAG]. */
const val DISMISS_TAG: String = "AppConfirmSheetDismiss"

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppConfirmSheetContentPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier3)
                .padding(AppDimension.screenEdge),
        ) {
            AppConfirmSheetContent(
                title = "Выйти без сохранения?",
                body = "Несохранённые правки будут потеряны.",
                confirmLabel = "Выйти без сохранения",
                dismissLabel = "Продолжить правку",
                onConfirm = {},
                onDismiss = {},
                confirmDestructive = true,
            )
        }
    }
}
