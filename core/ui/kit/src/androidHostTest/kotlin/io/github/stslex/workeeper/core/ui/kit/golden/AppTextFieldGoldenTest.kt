// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.input.AppFieldLabel
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The drawn `.tf` states in one frame: outline colour and weights, radius, heights, placeholder
 * tier, label above the box. The focused outline is unphotographed - Paparazzi has no input.
 */
internal class AppTextFieldGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun textFieldStates(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { TextFieldStates() }
    }
}

@Composable
private fun TextFieldStates() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Labelled and filled — the ordinary resting field, and the label above it.
        AppFieldLabel(text = "Название")
        AppTextField(value = "Bench press", onValueChange = {})
        // Empty — `.tf.ghosty`, the placeholder at `textDim`.
        AppTextField(value = "", onValueChange = {}, placeholder = "Exercise name")
        // `.tf.err` - the 1.5dp rust outline against the 1dp above it; the step is the signal.
        AppTextField(value = "Romanian deadlift", onValueChange = {}, isError = true)
        // `.tf.multi` — the same box, taller. 96dp, which is `heightMd` doubled.
        AppTextField(value = "", onValueChange = {}, placeholder = "Note", singleLine = false)
        // Disabled — undrawn, kept, and photographed so "undrawn" does not mean "unpinned".
        AppTextField(value = "Disabled", onValueChange = {}, enabled = false)
    }
}
