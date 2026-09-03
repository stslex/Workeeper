// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.core.ui.kit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_sheet_close
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/**
 * Phase 7.1's native gate: one production resource-backed kit composition rendered by the CMP
 * test runner's Skiko raster `ComposeScene` on the iOS simulator. It proves native composition,
 * Compose-resource string/font/icon loading, one advanced frame and the semantics tree — it does
 * NOT prove `ComposeUIViewController`, `UIWindow` or Metal/UIKit rendering; those claims belong
 * to the future `iosApp` stage. See kmp-phase-7-1-ui-kit.md.
 */
class IosKitSceneSmokeTest {

    @Test
    fun sheetLayoutRendersMigratedStringFontAndIcon() = runComposeUiTest {
        setContent {
            AppTheme {
                // AppSheetLayout paints the title with the migrated Plex Sans face and renders
                // the material Close icon labelled by the migrated core_ui_kit_sheet_close string.
                AppSheetLayout(
                    title = SHEET_TITLE,
                    onClose = {},
                ) { }
            }
        }

        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()

        val closeLabel = runBlocking { getString(Res.string.core_ui_kit_sheet_close) }
        onNodeWithText(SHEET_TITLE).assertIsDisplayed()
        onNodeWithContentDescription(closeLabel).assertIsDisplayed()
    }

    private companion object {
        const val SHEET_TITLE = "Native kit smoke"
    }
}
