// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetConfirmContent
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetMenuContent
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetMenuItem
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Sheet contents in all three forms, on the sheet's own `surfaceTier1`. The scrim, entry
 * animation and settle height live in the sheet's window, which Paparazzi does not model.
 */
internal class SheetAnatomyGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun menuForm(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            AppSheetLayout(title = "Set type") {
                AppSheetMenuContent(
                    items = listOf(
                        AppSheetMenuItem(title = "Warm-up", supporting = "Not counted", onClick = {}),
                        AppSheetMenuItem(title = "Working set", onClick = {}),
                        AppSheetMenuItem(title = "To failure", onClick = {}),
                    ),
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun confirmForm(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            AppSheetLayout(title = "Discard this session?") {
                AppSheetConfirmContent(
                    message = "Nothing was logged, so there is nothing to keep. " +
                        "The exercises you added inline will be removed too.",
                    confirmLabel = "Discard",
                    onConfirm = {},
                    dismissLabel = "Keep editing",
                    onDismiss = {},
                    confirmDestructive = true,
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun freeContentForm(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            AppSheetLayout(title = "About molten", onClose = {}) { FreeContent() }
        }
    }
}

@Composable
private fun FreeContent() {
    Text(
        modifier = Modifier.padding(horizontal = AppDimension.screenEdge),
        text = "Molten marks a personal record and nothing else. " +
            "It is not a general accent, and it is not available for a screen that wants colour.",
        style = AppUi.typography.text.body,
        color = AppUi.colors.textSecondary,
    )
}
