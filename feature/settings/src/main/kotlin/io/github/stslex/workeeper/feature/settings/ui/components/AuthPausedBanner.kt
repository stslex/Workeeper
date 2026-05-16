// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R

@Composable
internal fun AuthPausedBanner(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge)
            .clip(RoundedCornerShape(AppDimension.Space.sm))
            .background(AppUi.colors.surfaceTier2)
            .padding(AppDimension.Space.md)
            .testTag("AuthPausedBanner"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            text = stringResource(R.string.feature_settings_backup_auto_paused_banner_title),
            style = AppUi.typography.titleSmall,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.feature_settings_backup_auto_paused_banner_body),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textSecondary,
        )
        AppButton.Secondary(
            text = stringResource(R.string.feature_settings_backup_auto_paused_banner_action),
            onClick = onSignInClick,
            size = AppButtonSize.SMALL,
        )
    }
}

@Preview
@Composable
private fun AuthPausedBannerLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AuthPausedBanner(onSignInClick = {})
    }
}

@Preview
@Composable
private fun AuthPausedBannerDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AuthPausedBanner(onSignInClick = {})
    }
}
