// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.ui.components.AboutBlock
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsRow
import io.github.stslex.workeeper.feature.settings.ui.components.SettingsSection
import io.github.stslex.workeeper.feature.settings.ui.components.ThemeSelector

@Composable
internal fun SettingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("SettingsScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(R.string.feature_settings_title),
            navigationIcon = {
                IconButton(
                    modifier = Modifier.testTag("SettingsBackButton"),
                    onClick = { consume(Action.Navigation.OnBackClick) },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconMd),
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feature_settings_back),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimension.sectionSpacing),
        ) {
            SettingsSection(title = stringResource(R.string.feature_settings_about_section)) {
                AboutBlock(
                    appVersion = state.appVersion,
                    appVersionCode = state.appVersionCode,
                    onLicenseClick = { consume(Action.Click.OnLicenseClick) },
                    onGitHubClick = { consume(Action.Click.OnGitHubClick) },
                    onPrivacyClick = { consume(Action.Click.OnPrivacyPolicyClick) },
                )
            }
            SettingsSection(title = stringResource(R.string.feature_settings_appearance_section)) {
                ThemeSelector(
                    selected = state.themeMode,
                    onSelectedChange = { mode -> consume(Action.Input.OnThemeChange(mode)) },
                )
            }
            SettingsSection(title = stringResource(R.string.feature_settings_data_section)) {
                SettingsRow(
                    modifier = Modifier.testTag("SettingsArchiveRow"),
                    title = stringResource(R.string.feature_settings_archive_title),
                    onClick = { consume(Action.Click.OnArchiveClick) },
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            state = State(
                themeMode = ThemeMode.SYSTEM,
                appVersion = "1.0.0",
                appVersionCode = 15,
            ),
            consume = {},
        )
    }
}
