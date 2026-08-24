// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Acknowledge-style dialog for exercises that could not be archived because active trainings
 * still use them. Every display string arrives pre-formatted from the caller's UI mapper.
 */
@Composable
fun AppBlockedArchiveDialog(
    title: String,
    items: ImmutableList<BlockedArchiveItem>,
    nextStep: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    archivedSummary: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        AppBlockedArchiveDialogContent(
            title = title,
            items = items,
            nextStep = nextStep,
            confirmLabel = confirmLabel,
            onDismiss = onDismiss,
            modifier = modifier,
            archivedSummary = archivedSummary,
        )
    }
}

/**
 * The dialog's content without the window, so goldens can render the same pixels - Paparazzi
 * models a single window. Must stay a pure function of its arguments.
 */
@Composable
fun AppBlockedArchiveDialogContent(
    title: String,
    items: ImmutableList<BlockedArchiveItem>,
    nextStep: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    archivedSummary: String? = null,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
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
        if (archivedSummary != null) {
            Text(
                text = archivedSummary,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_LIST_HEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            items.forEach { item ->
                BlockedRow(item)
            }
        }
        Text(
            text = nextStep,
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
                text = confirmLabel,
                onClick = onDismiss,
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Composable
private fun BlockedRow(item: BlockedArchiveItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = item.exerciseName,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = item.trainingsLabel,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}

/** One blocked exercise; [trainingsLabel] arrives already formatted and truncated. */
data class BlockedArchiveItem(
    val exerciseName: String,
    val trainingsLabel: String,
)

private val MAX_LIST_HEIGHT = 220.dp

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppBlockedArchiveDialogPreview() {
    AppTheme {
        AppBlockedArchiveDialog(
            title = "Can't archive these",
            archivedSummary = "1 exercise archived",
            items = persistentListOf(
                BlockedArchiveItem("Bench press", "used in Push Day, Upper A"),
                BlockedArchiveItem("Squat", "used in Leg Day"),
            ),
            nextStep = "Remove them from those trainings first, then archive.",
            confirmLabel = "Got it",
            onDismiss = {},
        )
    }
}
