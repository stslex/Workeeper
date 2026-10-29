// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.feature.live_workout.ui.TopBar
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveWorkoutHeader
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * `.topbar` and `.shead` (extraction §1.2–1.3), at rest: three texts on `surfaceTier0`, no
 * card. `sheadEditing` only means anything paired with `sheadDefault` (§10.2).
 */
internal class SessionHeaderGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun topbar(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { TopBar(consume = {}) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheadDefault(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Shead(
                metaLabel = "1 из 5 упражнений · 4 из 18 подходов",
                isEditing = false,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheadSkippedMeta(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Shead(
                metaLabel = "2 из 4 упражнений · 9 из 14 подходов · пропущено 1",
                isEditing = false,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheadEditing(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Shead(
                metaLabel = "1 из 5 упражнений · 4 из 18 подходов",
                isEditing = true,
            )
        }
    }
}

@Composable
private fun Shead(metaLabel: String, isEditing: Boolean) {
    LiveWorkoutHeader(
        modifier = Modifier.padding(horizontal = AppDimension.screenEdge),
        trainingNameLabel = "верх (с подтягиваниями)",
        namePlaceholder = "Без названия",
        elapsedLabel = "12:04",
        metaLabel = metaLabel,
        isEditingName = isEditing,
        nameDraft = "верх (с подтягиваниями)",
        onNameTap = {},
        onNameChange = {},
        onNameSubmit = {},
        // Focus is skipped: it reaches the IME, which layoutlib cannot survive.
        requestFocusWhenEditing = false,
    )
}
