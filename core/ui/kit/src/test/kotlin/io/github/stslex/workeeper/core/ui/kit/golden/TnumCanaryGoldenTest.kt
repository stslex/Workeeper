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
 * Synthetic canary for O1 — deliberately not a design component.
 *
 * Archivo Expanded's digits are proportional: `0` is 769 units wide, `1` is 683. A live timer
 * set in it therefore *wobbles* — the whole string re-flows every time a `1` ticks over to a
 * `0` or back. That happens to sit exactly where the redesign puts a locked wow-moment, so it
 * is the last place a shifting baseline is acceptable.
 *
 * `fontFeatureSettings = "tnum"` fixes it by switching to tabular figures, where every digit
 * advances by the same amount. The trouble with that fix is that it is invisible in review: a
 * dropped `tnum` compiles, renders, and looks fine on any string whose digits happen not to
 * change.
 *
 * So this test renders `00:00` above `11:11` in the numeric family at the largest rung. With
 * tabular figures the two lines have identical advances and **the colons align vertically**.
 * Without them the `1`s are narrower, the second line contracts, and the colons visibly
 * separate — a pixel difference the gate cannot miss.
 *
 * Digits and a colon only: this is the numeric family, and O2 applies (see
 * `AppTypography.numericFontFamily`).
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
        // The widest and the narrowest digit the family has, stacked. Any advance-width
        // difference shows up as horizontal drift between the two colons.
        NUMERIC_LADDER.forEach { line ->
            Text(
                text = line,
                style = AppUi.typography.numeric.display,
                color = AppUi.colors.textPrimary,
            )
        }
    }
}

private val NUMERIC_LADDER = listOf("00:00", "11:11")
