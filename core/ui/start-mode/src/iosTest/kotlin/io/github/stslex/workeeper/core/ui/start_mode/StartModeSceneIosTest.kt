// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.core.ui.start_mode

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.core.ui.start_mode.resources.Res
import io.github.stslex.workeeper.core.ui.start_mode.resources.core_ui_start_mode_name_days_since_last
import io.github.stslex.workeeper.core.ui.start_mode.resources.core_ui_start_mode_name_forgotten_training
import io.github.stslex.workeeper.core.ui.start_mode.resources.core_ui_start_mode_name_lagging_groups
import io.github.stslex.workeeper.core.ui.start_mode.resources.core_ui_start_mode_name_week
import io.github.stslex.workeeper.core.ui.start_mode.resources.core_ui_start_mode_sheet_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class StartModeSceneIosTest {

    @Test
    fun sheetRendersMigratedCatalogAndDispatchesSelection() = runComposeUiTest {
        val selections = mutableListOf<StartCardModeUi>()

        setContent {
            AppTheme {
                StartCardModeSheetContent(
                    selected = StartCardModeUi.WEEK,
                    onSelect = selections::add,
                )
            }
        }

        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()

        val expectedTexts = runBlocking {
            listOf(
                getString(Res.string.core_ui_start_mode_sheet_title),
                getString(Res.string.core_ui_start_mode_name_week),
                getString(Res.string.core_ui_start_mode_name_days_since_last),
                getString(Res.string.core_ui_start_mode_name_lagging_groups),
                getString(Res.string.core_ui_start_mode_name_forgotten_training),
            )
        }
        expectedTexts.forEach { text ->
            onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        }

        onNodeWithTag("StartCardModeCheck_WEEK", useUnmergedTree = true).assertExists()
        StartCardModeUi.entries
            .filterNot { mode -> mode == StartCardModeUi.WEEK }
            .forEach { mode ->
                onNodeWithTag(
                    "StartCardModeCheck_${mode.name}",
                    useUnmergedTree = true,
                ).assertDoesNotExist()
            }

        assertEquals(emptyList(), selections)
        onNodeWithTag(
            "StartCardModeRow_LAGGING_GROUPS",
            useUnmergedTree = true,
        ).performClick()
        assertEquals(listOf(StartCardModeUi.LAGGING_GROUPS), selections)
    }
}
