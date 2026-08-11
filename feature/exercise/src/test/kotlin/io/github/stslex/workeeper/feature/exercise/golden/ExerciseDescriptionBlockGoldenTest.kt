// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDescriptionBlock
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The `ОПИСАНИЕ` block — **one component, so both its modes are photographed** (`v3-editors.md`
 * §7's golden list, D-OPEN-9). S2 builds it and S3 consumes it, so the editable mode has no host
 * on this branch; a mode with no host and no picture is a mode nobody has looked at.
 *
 * Three states, and each is a difference partner of the one before it:
 *
 *  - **with a picture** — the box is a solid-bordered gradient and carries no glyph;
 *  - **without one** — dashed border, the exercise's type mark, and on read no destination at
 *    all, which is the state D-OPEN-3 leaves this screen in: read cannot pick an image, so the
 *    box is drawn and is not a control;
 *  - **editable** — the same picture slot beside `.tf.multi` instead of a paragraph.
 *
 * Paparazzi decodes no image, so the "with a picture" frame shows the filled box's own treatment
 * rather than a photograph. That IS the assertion: the has/has-not signal is the border and the
 * fill, not the picture, and `ExerciseThumbGoldenTest` photographs the same distinction with a
 * flat `Box` stand-in for the same reason.
 *
 * Rendered in **Russian** — the placeholder line and the description are what a Russian user
 * reads, and the block's whole purpose is to carry text.
 */
internal class ExerciseDescriptionBlockGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readWithImage(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            ExerciseDescriptionBlock(
                modifier = Modifier.padding(SUBJECT_INSET),
                description = DESCRIPTION,
                type = ExerciseTypeUiModel.WEIGHTED,
                imageDisplay = ImageDisplay.FromPath(path = "/exercise/preview.jpg", lastModified = 1L),
                onOpenImage = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readWithPlaceholder(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            ExerciseDescriptionBlock(
                modifier = Modifier.padding(SUBJECT_INSET),
                description = DESCRIPTION,
                type = ExerciseTypeUiModel.WEIGHTLESS,
                imageDisplay = ImageDisplay.None,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editable(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            ExerciseDescriptionBlock(
                modifier = Modifier.padding(SUBJECT_INSET),
                description = "",
                type = ExerciseTypeUiModel.WEIGHTED,
                imageDisplay = ImageDisplay.None,
                onPickImage = {},
                onDescriptionChange = {},
            )
        }
    }
}

private const val DESCRIPTION =
    "Разводи гантели в стороны до уровня плеч, локти чуть согнуты."

/** The screen edge the host puts around it, so the block is not flush to the canvas. */
private val SUBJECT_INSET = 16.dp
