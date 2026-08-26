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
 * The segmented control: one lifted thumb beside resting segments, so the lifted/resting pair
 * is inside one image. Three segments, so a divider between two resting ones would show.
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
