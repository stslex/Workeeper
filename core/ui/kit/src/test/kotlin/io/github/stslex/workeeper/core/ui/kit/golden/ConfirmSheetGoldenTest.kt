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
 * The confirmation **sheet** that every modal on the three editors is (§26; extraction §7.4).
 *
 * **What one frame can hold here, and it is most of the ruling.** That there are **TWO** actions
 * and not three — a save action returning to a discard sheet is a diff in this picture. That the
 * destructive one is **first and in `status.error`**, so the dangerous action
 * is the one you read first rather than the one you reach last. That the actions are **`.mitem`
 * rows**, not buttons — full-width, tappable to the edge, which is what the drawing draws. And that
 * the title, body and items sit at the drawn rungs on the sheet's own `surfaceTier3`.
 *
 * **What it cannot hold, per the harness KDoc and §27:** the window. `ModalBottomSheet` renders in
 * its own window and Paparazzi models one, so the scrim, the drag handle, the entry animation and
 * the settled height are on the PR's manual checklist. That is why `AppConfirmSheetContent` exists
 * separately at all — the half that carries every colour and rung is the half a picture can gate.
 *
 * The type-change frame is here because it is the one caller with an [emphasis] line, and because
 * that line is the ruling's own compromise: `AppConfirmDialog` put an impact summary on a
 * `failureBackground` panel, the drawn sheet has no panel, and this renders it a tier brighter
 * instead. A frame is the only thing that can show "brighter, not boxed" is what shipped.
 *
 * Russian, because these are the strings the app renders and the discard sheet's four are the
 * ones §26 rewrote — «Продолжить правку» in place of «Отмена» is a decision, and an `en` frame
 * would photograph a translation of it rather than it.
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
