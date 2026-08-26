// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exercise thumb and its two type marks. The frame holds three claims: the marks differ from
 * each other, empty is dashed while filled is solid, and both are strokes rather than fills.
 */
internal class ExerciseThumbGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun thumbStates(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Thumbs() }
    }
}

@Composable
private fun Thumbs() {
    Row(
        modifier = Modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppExerciseThumb(isWeighted = true, onClick = {}, contentDescription = "Add a photo")
        AppExerciseThumb(isWeighted = false, onClick = {}, contentDescription = "Add a photo")
        AppExerciseThumb(isWeighted = true, onClick = {}, contentDescription = "Open photo") {
            Box(modifier = Modifier.fillMaxSize().background(AppUi.colors.surfaceTier4))
        }
    }
}
