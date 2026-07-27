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
 * The v3 `--slabtop` lifted surface, **as a pair** — §10.2.
 *
 * A lone lifted golden would assert nothing about when lifting applies: whatever it captured
 * would become the baseline, including "lifted always" or "lifted never". The pair is what
 * carries the claim, and it carries it twice over, because the mechanism inverts by theme:
 *
 * - `[1] LIGHT` resting vs lifted must differ by a **cast shadow** and a `slab` fill;
 * - `[2] DARK` resting vs lifted must differ by a **1dp top-edge highlight** and a `slab` fill.
 *
 * If either mechanism silently stopped working, its theme's two images would converge to the
 * same picture apart from the fill, and the diff against these baselines is what says so.
 *
 * Rendered on `surfaceTier0` deliberately: the light half casts onto the page, and a golden that
 * put it on a card would be measuring a shadow against the wrong backdrop.
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

/**
 * Padded on all sides so the light theme's cast shadow has canvas to fall on — `SHRINK` sizes the
 * image to the content, and a shadow drawn outside the specimen's own bounds would otherwise be
 * cropped away by exactly the amount that makes it visible.
 */
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
