// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Synthetic canary — deliberately **not** a design component.
 *
 * The redesign removes cards, which leaves the hairline as the only section separator in the
 * app. That makes sub-pixel rule rendering the single highest flake risk in the whole visual
 * gate: at the pinned 440 dpi a `1.dp` rule is 2.75 physical pixels and a `0.5.dp` rule is
 * 1.375, so neither lands on a pixel boundary. If layoutlib resolved that nondeterministically,
 * every future golden carrying a separator would flake at `maxPercentDifference = 0.0`.
 *
 * **Answer, measured:** it does not. Layoutlib snaps both to whole pixels — the `1.dp` rule
 * rasterises as 3 rows of one flat colour and the `0.5.dp` rule as exactly 1 row, with no
 * partial-alpha edge at any phase, identically in both themes. Hairlines are crisp and stable,
 * so the redesign can lean on them. See `GOLDEN_DEVICE` for why the goldens are recorded at
 * native resolution — a scaled image reintroduces exactly the blur this test rules out.
 *
 * This test answers that question on one image instead of on a hundred. The spacer heights are
 * intentionally odd — `7.dp`, `10.5.dp`, `13.dp`, `0.5.dp` — so successive rules accumulate
 * fractional offsets and start at different sub-pixel phases down the frame. A rule that is
 * crisp at one phase and blurred at another is exactly the behaviour worth knowing about
 * before it is load-bearing.
 *
 * A failure here is a NO-GO finding for the hairline golden category, to be reported. It is
 * never a reason to raise the tolerance.
 */
internal class HairlineCanaryGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun hairlines(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) { HairlineLadder() }
    }
}

@Composable
private fun HairlineLadder() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SUB_PIXEL_PHASES.forEach { phase ->
            Box(modifier = Modifier.height(phase))
            Rule(thickness = 1.dp)
            Box(modifier = Modifier.height(phase))
            Rule(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun Rule(thickness: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thickness)
            // One colour for both thicknesses, so the only variable under test is the
            // sub-pixel geometry.
            .background(AppUi.colors.borderDefault),
    )
}

/**
 * Chosen so the running offset lands on a different fraction of a physical pixel each time:
 * at density 3.0 these are 21, 31.5, 39 and 1.5 px respectively.
 */
private val SUB_PIXEL_PHASES = listOf(7.dp, 10.5.dp, 13.dp, 0.5.dp)
