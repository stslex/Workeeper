// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.settings.R

@Composable
internal fun AboutBlock(
    appVersion: String,
    appVersionCode: Int,
    onLicenseClick: () -> Unit,
    onGitHubClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            text = stringResource(R.string.feature_settings_about_app_name),
            style = AppUi.typography.titleMedium,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.feature_settings_about_version_format, appVersion, appVersionCode),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
        Text(
            modifier = Modifier
                .clickable(onClick = onLicenseClick)
                .padding(vertical = AppDimension.Space.xxs),
            text = stringResource(R.string.feature_settings_about_license),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.accent,
        )
        Text(
            modifier = Modifier
                .clickable(onClick = onGitHubClick)
                .padding(vertical = AppDimension.Space.xxs),
            text = stringResource(R.string.feature_settings_about_github),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.accent,
        )
        Text(
            modifier = Modifier
                .clickable(onClick = onPrivacyClick)
                .padding(vertical = AppDimension.Space.xxs),
            text = stringResource(R.string.feature_settings_about_privacy),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.accent,
        )
    }
}
