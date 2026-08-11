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
 * `.topbar` and `.shead` (extraction §1.2–1.3), at rest.
 *
 * The header is the screen region step 5 got most visibly wrong — rendered as a card — so
 * these goldens lock the corrected picture: three texts on `surfaceTier0`, the training name
 * on the 26 rung, the meta line in mono, and the timer through `AppTypography.timer` —
 * Archivo `wdth 116`'s first production call site.
 *
 * The name strings are Cyrillic on purpose: they exercise the real `text.title` rendering
 * path for the app's primary locale, while the timer glyphs stay inside Archivo's
 * digits-and-separators charset (spec C2).
 *
 * `sheadEditing` pairs with `sheadDefault` (§10.2): the edit field must reproduce the `h2`
 * treatment exactly, and only the pair shows it — a lone editing golden would lock whatever
 * the field happened to render.
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
        // requestFocus() reaches for the IME, and layoutlib's HandlerThread delegate dies on
        // the host JVM — racily. The golden asserts pixels, not focus; see the field's KDoc.
        requestFocusWhenEditing = false,
    )
}
