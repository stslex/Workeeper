// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.switch.AppSwitch
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The v3 `.sw` at both poles (extraction §1.9): OFF = `borderDefault` track / `meta` knob
 * (the measured stand-ins for the mockup's invisible `hair-s`), ON = `max` track / `base`
 * knob. One golden with both states side by side — the pair IS the assertion.
 */
internal class AppSwitchGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun switchOffAndOn(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Switches() }
    }
}

@Composable
private fun Switches() {
    Row(
        modifier = Modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppSwitch(checked = false, onCheckedChange = {})
        AppSwitch(checked = true, onCheckedChange = {})
    }
}
