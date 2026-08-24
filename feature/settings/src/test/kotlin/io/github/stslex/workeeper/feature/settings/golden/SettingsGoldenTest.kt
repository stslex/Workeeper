// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupAuthUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupInfoUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupPreferencesUi
import io.github.stslex.workeeper.feature.settings.mvi.model.BackupScheduleUi
import io.github.stslex.workeeper.feature.settings.mvi.model.RestoreProgressUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.ui.SettingsScreen
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The settings golden suite. The three `DialogState` surfaces are windows and out of the
 * harness's model; `RestoreProgressOverlay` is not, so both its variants are recorded.
 */
internal class SettingsGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSignedOut(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            SettingsScreen(state = baseState(), consume = {})
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenSignedIn(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            SettingsScreen(state = signedInState(), consume = {})
        }
    }

    /** The segmented thumb's far travel endpoint; the default frames hold the near one. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenThemeDark(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            SettingsScreen(
                state = baseState().copy(themeMode = ThemeMode.DARK),
                consume = {},
            )
        }
    }

    /** The auth-paused banner — a code-only surface the mockup does not draw. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenAuthPaused(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            SettingsScreen(
                state = signedInState().let { state ->
                    state.copy(
                        backupPreferences = state.backupPreferences?.copy(isAuthPaused = true),
                    )
                },
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenRestoreOverlayRestoring(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            SettingsScreen(
                state = signedInState().copy(restoreProgress = RestoreProgressUi.Restoring),
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenRestoreOverlayCompleted(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            SettingsScreen(
                state = signedInState().copy(restoreProgress = RestoreProgressUi.Completed),
                consume = {},
            )
        }
    }
}

private fun baseState(): State = State.initial(
    appVersion = "1.48.0",
    appVersionCode = 49,
).copy(
    // Fixture holds the default so the sub-line renders rather than the pre-emission blank.
    startCardMode = StartCardModeUi.WEEK,
)

/** The fullest signed-in surface: every branch `BackupSection` can show, at once. */
private fun signedInState(): State = baseState().copy(
    backupAuth = BackupAuthUi.Authenticated(
        email = "user@example.com",
        displayName = "User",
    ),
    backupInfo = BackupInfoUi.Present(
        lastBackupText = "last a minute ago",
        backupCountText = "3 backups",
    ),
    backupPreferences = BackupPreferencesUi(
        schedule = BackupScheduleUi.DAILY,
        allowOnMobileData = false,
        // The row's sub-line template supplies the "next" word; this is the bare value.
        nextBackupText = "in 23 h",
        isAuthPaused = false,
        aiExportEnabled = true,
    ),
    canRevertLastRestore = true,
)
