// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Leaf-primitive golden. `AppButton.Primary` is the proven one: it reads `accent` / `onAccent`
 * / `surfaceTier4` / `textDisabled` straight off [io.github.stslex.workeeper.core.ui.kit.theme.AppUi],
 * so a palette change has nowhere to hide, and the disabled row pins the disabled pair too.
 */
internal class AppButtonGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun primaryButton(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) { PrimaryButtons() }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun ghostAndDangerButtons(theme: GoldenTheme, testInfo: TestInfo) {
        // The v3 sheet pair (extraction §1.8): `.btn.ghost` = field fill / body text,
        // `.btn.danger` = text-only rust at 500. Recorded together — they are drawn as a
        // stack in `sh-del` and read as a pair.
        golden(testInfo, theme) { GhostAndDangerButtons() }
    }
}

@androidx.compose.runtime.Composable
private fun GhostAndDangerButtons() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppButton.Ghost(
            text = "Оставить",
            onClick = {},
        )
        AppButton.DangerText(
            text = "Удалить из плана",
            onClick = {},
        )
    }
}

@androidx.compose.runtime.Composable
private fun PrimaryButtons() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppButton.Primary(
            text = "Start workout",
            onClick = {},
            size = AppButtonSize.LARGE,
        )
        AppButton.Primary(
            text = "Medium",
            onClick = {},
            size = AppButtonSize.MEDIUM,
        )
        AppButton.Primary(
            text = "Small",
            onClick = {},
            size = AppButtonSize.SMALL,
        )
        AppButton.Primary(
            text = "Disabled",
            onClick = {},
            enabled = false,
        )
    }
}
