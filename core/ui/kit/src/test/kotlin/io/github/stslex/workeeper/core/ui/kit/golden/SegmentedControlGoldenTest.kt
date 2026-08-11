// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.segmented.AppSegmentedControl
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The mockup's `.mseg`, and the second consumer of the lifted surface.
 *
 * The lifted/unlifted pair §10.2 asks for is *inside* one image here rather than across two: a
 * segmented control always shows exactly one lifted thumb next to at least one resting segment,
 * so the discriminating picture is the control itself. A golden of a control with everything
 * lifted, or nothing lifted, would be a different image — which is the assertion.
 *
 * Three segments, not two, so the middle one pins that a resting segment between two others is
 * air and not a rule: the hairline dividers this component used to draw would show here.
 */
internal class SegmentedControlGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun segmentedControl(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Segments() }
    }
}

@Composable
private fun Segments() {
    AppSegmentedControl(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimension.Space.xl),
        items = persistentListOf("Weight", "Session", "Set"),
        selected = 1,
        onSelectedChange = {},
    )
}
