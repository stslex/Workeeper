// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Synthetic canary, not a design component: `00:00` over `11:11` in the numeric family, whose
 * colons align only while `tnum` is applied. A dropped `tnum` is otherwise invisible in review.
 */
internal class TnumCanaryGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tnumCanary(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) { TabularFigureLadder() }
    }
}

@Composable
private fun TabularFigureLadder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // The widest and narrowest digits stacked; any advance difference drifts the colons.
        NUMERIC_LADDER.forEach { line ->
            Text(
                text = line,
                // Through `timer`, the alias the session screen calls, so it must stay tabular.
                style = AppUi.typography.timer,
                color = AppUi.colors.textPrimary,
            )
        }
    }
}

private val NUMERIC_LADDER = listOf("00:00", "11:11")
