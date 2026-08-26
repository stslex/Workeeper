// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheetContent
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The confirmation sheet content every editor modal uses: two actions, the destructive one first
 * in `status.error`, as full-width rows. The window itself stays on the manual checklist.
 */
internal class ConfirmSheetGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun discardSheet(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU, surface = { sheetSurface() }) {
            OnSheet {
                AppConfirmSheetContent(
                    title = "Выйти без сохранения?",
                    body = "Несохранённые правки будут потеряны.",
                    confirmLabel = "Выйти без сохранения",
                    dismissLabel = "Продолжить правку",
                    onConfirm = {},
                    onDismiss = {},
                    confirmDestructive = true,
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun typeChangeSheet(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU, surface = { sheetSurface() }) {
            OnSheet {
                AppConfirmSheetContent(
                    title = "Сделать упражнение без веса?",
                    body = "Вес перестанет учитываться в плане и в графике.",
                    emphasis = "Вес будет очищен в 3 строках плана.",
                    confirmLabel = "Сделать без веса",
                    dismissLabel = "Отмена",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}

/** `AppBottomSheet` is the window; this is the padding it puts around the content. */
@Composable
private fun sheetSurface(): Color = AppUi.colors.surfaceTier3

@Composable
private fun OnSheet(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier3)
            .padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                top = AppDimension.Space.sm,
                bottom = AppDimension.Space.xxl,
            ),
    ) {
        content()
    }
}
