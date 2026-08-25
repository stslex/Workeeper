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
 * Both modes of the `ОПИСАНИЕ` block, in Russian. Paparazzi decodes no image, so the "with a
 * picture" frame photographs the filled box's own treatment — border and fill are the signal.
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
