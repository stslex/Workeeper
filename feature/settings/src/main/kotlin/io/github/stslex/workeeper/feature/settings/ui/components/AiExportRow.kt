// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListItem
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R

/**
 * Toggle row for the AI-readable JSON snapshot export. The supporting text is the consent
 * disclosure required by the spec (§7): the snapshot is plaintext workout data in the user's
 * *visible, shareable* Drive — not the hidden app-data backup. Shown only when authenticated.
 *
 * While [inProgress] (the incremental `drive.file` grant is mid-flight) the row is non-interactive
 * and shows a spinner in place of the switch, so a re-tap can't launch a second grant request.
 */
@Composable
internal fun AiExportRow(
    enabled: Boolean,
    inProgress: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppListItem(
        modifier = modifier
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.sm,
            )
            .testTag("AiExportRow"),
        headline = stringResource(R.string.feature_settings_backup_ai_export_label),
        supportingText = stringResource(R.string.feature_settings_backup_ai_export_caption),
        onClick = { if (!inProgress) onToggle(!enabled) },
        trailingContent = {
            if (inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimension.iconSm),
                    strokeWidth = AppDimension.Space.xxs,
                    color = AppUi.colors.accent,
                )
            } else {
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        },
    )
}

@Preview
@Composable
private fun AiExportRowOffPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AiExportRow(enabled = false, inProgress = false, onToggle = {})
    }
}

@Preview
@Composable
private fun AiExportRowOnPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AiExportRow(enabled = true, inProgress = false, onToggle = {})
    }
}

@Preview
@Composable
private fun AiExportRowInProgressPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AiExportRow(enabled = false, inProgress = true, onToggle = {})
    }
}
