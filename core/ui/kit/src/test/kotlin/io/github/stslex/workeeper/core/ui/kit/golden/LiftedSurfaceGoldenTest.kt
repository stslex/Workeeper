// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The lifted surface as a resting/lifted pair — a lone frame would assert nothing about when
 * lifting applies. Light lifts by cast shadow, dark by a top-edge highlight.
 */
internal class LiftedSurfaceGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun surfaceResting(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { LiftSpecimen(lifted = false) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun surfaceLifted(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { LiftSpecimen(lifted = true) }
    }
}

// GUARD: padded on all sides so the light theme's cast shadow has canvas to fall on — SHRINK
// sizes the image to the content and would crop the very thing under test.
@Composable
private fun LiftSpecimen(lifted: Boolean) {
    Box(modifier = Modifier.padding(AppDimension.Space.xl)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SPECIMEN_HEIGHT)
                .liftedSurface(shape = AppUi.shapes.medium, lifted = lifted)
                .padding(AppDimension.Space.lg),
        ) {
            Text(
                text = if (lifted) "lifted" else "resting",
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
        }
    }
}

private val SPECIMEN_HEIGHT: Dp = 88.dp
