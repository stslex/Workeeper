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
 * Synthetic canary, not a design component: proves layoutlib snaps sub-pixel rules to whole
 * pixels, so hairlines are stable at `maxPercentDifference = 0.0`. A failure here is a NO-GO.
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
            // One colour for both thicknesses, so only the sub-pixel geometry varies.
            .background(AppUi.colors.borderDefault),
    )
}

/** Chosen so the running offset lands on a different fraction of a physical pixel each time. */
private val SUB_PIXEL_PHASES = listOf(7.dp, 10.5.dp, 13.dp, 0.5.dp)
