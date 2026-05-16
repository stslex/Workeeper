// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                AppUi.colors.surfaceTier0,
            )
            .border(
                width = AppDimension.Border.medium,
                shape = RoundedCornerShape(AppDimension.Radius.medium),
                color = AppUi.colors.borderSubtle,
            )
            .padding(AppDimension.Space.md),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = AppDimension.screenEdge),
            text = title,
            style = AppUi.typography.labelSmall,
            color = AppUi.colors.textTertiary,
        )
        content()
    }
}
