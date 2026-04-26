// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R

@Composable
internal fun ThemeSelector(
    selected: ThemeMode,
    onSelectedChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        ThemeOption(
            label = stringResource(R.string.feature_settings_theme_system),
            selected = selected == ThemeMode.SYSTEM,
            tag = "ThemeOption_SYSTEM",
            onClick = { onSelectedChange(ThemeMode.SYSTEM) },
        )
        ThemeOption(
            label = stringResource(R.string.feature_settings_theme_light),
            selected = selected == ThemeMode.LIGHT,
            tag = "ThemeOption_LIGHT",
            onClick = { onSelectedChange(ThemeMode.LIGHT) },
        )
        ThemeOption(
            label = stringResource(R.string.feature_settings_theme_dark),
            selected = selected == ThemeMode.DARK,
            tag = "ThemeOption_DARK",
            onClick = { onSelectedChange(ThemeMode.DARK) },
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppDimension.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        RadioButton(
            modifier = Modifier.testTag(tag),
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppUi.colors.accent,
                unselectedColor = AppUi.colors.borderStrong,
            ),
        )
        Text(
            text = label,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
    }
}
