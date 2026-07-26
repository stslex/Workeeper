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
