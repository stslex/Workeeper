// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.start_mode.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.start_mode.StartCardModeSheetContent
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The mode sheet's CONTENT on the surface it actually sits on (`surfaceTier3`, the
 * `AppBottomSheet` window fill) — the window itself (scrim, grab handle, entrance) renders
 * out of Paparazzi's model and stays on the device checklist, same split as the session
 * sheets. Rendered at `values-ru` because every string on this surface is shipped RU copy
 * ruled verbatim by the arc; an `en`-locale picture would gate the fallback translations
 * instead of the contract.
 */
internal class StartCardModeSheetGoldenTest {

    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        Box(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
            content()
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun modeSheet(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(
            testInfo,
            theme,
            locale = LOCALE_RU,
            surface = { AppUi.colors.surfaceTier3 },
        ) {
            SheetFrame {
                StartCardModeSheetContent(
                    selected = StartCardModeUi.WEEK,
                    onSelect = {},
                )
            }
        }
    }
}
