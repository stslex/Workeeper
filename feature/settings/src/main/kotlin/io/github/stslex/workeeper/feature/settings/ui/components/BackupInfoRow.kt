// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
internal fun BackupInfoRow(
    lastBackupText: String,
    backupCountText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = lastBackupText,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
        Text(
            text = backupCountText,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}
