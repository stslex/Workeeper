// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.mvi.store.DialogState
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Reds if `SettingsScreen` reinstates a `?: StartCardModeUi.WEEK` default at the
 * `StartCardModeSheet(selected = ...)` call site: the sheet must check nothing until the
 * preference is known. Its subject is the wiring, not the sheet.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class SettingsStartCardModeSheetTest {

    @Test
    fun theSheetChecksNothingUntilThePreferenceIsKnown() = runComposeUiTest {
        val state = mutableStateOf(pickerOpen(startCardMode = null))
        setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(state = state.value, consume = {})
            }
        }
        waitForIdle()

        // The sheet is up, but answers nothing yet.
        onNodeWithTag(SHEET).assertExists()
        StartCardModeUi.entries.forEach { mode ->
            onNodeWithTag(row(mode), useUnmergedTree = true).assertExists()
            onNodeWithTag(check(mode), useUnmergedTree = true).assertDoesNotExist()
        }

        // Deliberately not WEEK: a resolved WEEK is pixel-identical to the forbidden guess.
        runOnIdle { state.value = pickerOpen(startCardMode = StartCardModeUi.LAGGING_GROUPS) }
        waitForIdle()

        onNodeWithTag(check(StartCardModeUi.LAGGING_GROUPS), useUnmergedTree = true)
            .assertExists()
        StartCardModeUi.entries
            .filterNot { it == StartCardModeUi.LAGGING_GROUPS }
            .forEach { mode ->
                onNodeWithTag(check(mode), useUnmergedTree = true).assertDoesNotExist()
            }
    }

    private fun pickerOpen(startCardMode: StartCardModeUi?): State = State
        .initial(appVersion = "1.0.0", appVersionCode = 15)
        .copy(
            startCardMode = startCardMode,
            dialogState = DialogState.StartCardModePicker,
        )

    private fun row(mode: StartCardModeUi): String = "StartCardModeRow_${mode.name}"

    private fun check(mode: StartCardModeUi): String = "StartCardModeCheck_${mode.name}"

    private companion object {

        const val SHEET = "StartCardModeSheet"
    }
}
