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
 * HS6's sheet half: the mode sheet opened from Settings is a **readout of the persisted
 * preference**, so it checks nothing until that preference is known.
 *
 * The Settings row's sub-line has held this line since it was built — blank until
 * `SettingsPagingHandler.observeStartCardMode()`'s first emission, "no guessed default". The
 * sheet under it did not: it substituted `?: StartCardModeUi.WEEK` at the call site, so a tap
 * landing before that emission opened a window with WEEK checked whatever the persisted mode
 * actually was. The substitution is indistinguishable from a real reading, which is what makes
 * it worse than an empty one — and it is wrong for every user on one of the other three modes.
 *
 * **This suite exists to red on that substitution's return.** Reinstate the elvis at the
 * `StartCardModeSheet(selected = ...)` call site in `SettingsScreen` and the first half fails:
 * `StartCardModeCheck_WEEK` appears while the state's `startCardMode` is still null. That is
 * why the subject is `SettingsScreen` and not `StartCardModeSheetContent` — the sheet's own
 * contract is already honest, and the defect lives in the wiring.
 *
 * ## Why a JVM test, and why this shape
 *
 * The claim is a semantics one — which row carries the check — and neither instrument that
 * already gates this surface can make it. Paparazzi photographs `StartCardModeSheetContent`
 * directly and never sees `SettingsScreen`'s call site (the sheet is a window, out of the
 * golden harness's model); a handler test never composes. `src/androidTest` would not do
 * either: `ui_tests.yml` is `workflow_dispatch`-only, so an assertion there is not a gate.
 * Robolectric plus `runComposeUiTest` runs under `testDebugUnitTest` on every PR — the shape
 * `core:ui:kit`'s `AccessibilitySemanticsTest` established, whose KDoc records the two
 * constraints obeyed here: `runComposeUiTest` rather than a JUnit 4 `createComposeRule` (this
 * repo's test tasks are `useJUnitPlatform()`, with no vintage engine — a JUnit 4-shaped class
 * in `src/test` is silently not run), and **one `@Test`, one composition**, because a second
 * environment in the same Robolectric sandbox hangs rather than fails. Both halves of the
 * claim therefore share one composition and are separated by a state change, which is also
 * the truer subject: the unresolved frame is the one the real screen renders first, and the
 * resolved one is what DataStore's first emission turns it into.
 *
 * `useUnmergedTree` throughout: each row is `clickable`, which merges its descendants, so the
 * check's tag is not reachable in the merged tree.
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

        // The sheet IS up — the tap that opened it did something. What it does not do is
        // answer a question it has not been told the answer to.
        onNodeWithTag(SHEET).assertExists()
        StartCardModeUi.entries.forEach { mode ->
            onNodeWithTag(row(mode), useUnmergedTree = true).assertExists()
            onNodeWithTag(check(mode), useUnmergedTree = true).assertDoesNotExist()
        }

        // The preference lands, and on a mode that is NOT the default — a resolved WEEK is
        // pixel-for-pixel the guess this test exists to forbid, so it could not tell them
        // apart.
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
