// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.ui.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersScreenRootAndArchiveRow() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    state = State(
                        themeMode = ThemeMode.LIGHT,
                        appVersion = "1.0",
                        appVersionCode = 1,
                    ),
                    consume = capture,
                )
            }
        }

        composeTestRule.onNodeWithTag("SettingsScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SettingsArchiveRow")
            .assertIsDisplayed()
            .performClick()
        capture.assertCaptured<Action.Click.OnArchiveClick>()
    }

    @Test
    fun backButtonEmitsBackNavigation() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    state = State(
                        themeMode = ThemeMode.LIGHT,
                        appVersion = "1.0",
                        appVersionCode = 1,
                    ),
                    consume = capture,
                )
            }
        }

        composeTestRule.onNodeWithTag("SettingsBackButton").performClick()
        capture.assertCaptured<Action.Navigation.Back>()
    }
}
