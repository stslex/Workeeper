// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTag
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The v3 `.tag` (extraction §3.2 / §4.4), resting and `.on` side by side — the pair is the
 * difference assertion: fill `sec` → `raise`, label `meta` → `max`, and the ring that only
 * the selected pill carries.
 */
internal class AppTagGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun tagRestingAndSelected(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Tags() }
    }
}

@Composable
private fun Tags() {
    Row(
        modifier = Modifier.padding(AppDimension.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        AppTag(label = "С весом")
        AppTag(label = "верх")
        AppTag(label = "Всё", selected = true, onClick = {})
    }
}
