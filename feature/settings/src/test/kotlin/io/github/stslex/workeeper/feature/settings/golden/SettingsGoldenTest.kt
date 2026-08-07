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
 * The settings golden suite. The BASELINE commit (S0) records the pre-rebuild surface —
 * `SettingsSection`'s 2dp bordered boxes in the About → Appearance → Backup → Data order,
 * radio-button theme rows, `AppButton.Secondary` backup rows — so each Part-5 rebuild
 * commit reads as an image diff.
 *
 * Fixture data mirrors `pass2d.html` §`s-set` where the mockup draws it (the account row's
 * placeholder email/name, daily schedule, three stored backups) so the final
 * element-by-element pass holds golden beside mockup with no renaming.
 *
 * Out of model, per the harness KDoc: the three `DialogState` surfaces — both
 * `AppConfirmDialog`s and the `FrequencyPickerBottomSheet` are windows (§10.4, device
 * checklist). The `RestoreProgressOverlay` is NOT a window (a scrim `Box` in the root) and
 * is recorded in both variants.
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

    /**
     * The mseg thumb's transient pair (§10.2): both travel endpoints — the default frames
     * hold the thumb on the first stop (SYSTEM); this one parks it on the last (DARK) with
     * the sub-line following («Тёмная»). The lift/colour transitions between stops are
     * time-based and outside the gate.
     */
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
    // The Appearance group's second row (HS5): fixture holds the default so the sub-line
    // renders «Неделя» rather than the pre-emission blank.
    startCardMode = StartCardModeUi.WEEK,
)

/**
 * The fullest signed-in surface: account row, auto-backup with a next-run line, AI export
 * on, backup info, and the conditional revert row — every branch `BackupSection` can show
 * at once.
 */
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
