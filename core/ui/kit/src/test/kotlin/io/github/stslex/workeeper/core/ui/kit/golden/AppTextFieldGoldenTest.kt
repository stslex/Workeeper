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
 * The drawn `.tf` states, in one frame (extraction §7.2).
 *
 * **What this photograph is for, and what it is not.** It pins the things a single static frame
 * genuinely holds: that the field is **outlined and unfilled** (a container fill would flood the
 * box), the outline's colour, the **1.5dp** error weight against the 1dp resting weight, the 8dp
 * radius, the 48dp resting height against the 96dp multiline one, the placeholder's tier, and the
 * label sitting **above** the box rather than floating inside it. Those are the properties §26 "The
 * editors' text field" rules, and every one of them is visible without a gesture.
 *
 * **What it cannot see, said here rather than left to be assumed (§27, "a golden image gates only
 * what a single static frame contains"):** the focused outline. Paparazzi renders one frame with no
 * input, so `collectIsFocusedAsState` is always false and the `accent` branch is unphotographed —
 * it is also the one branch the drawing does not draw, which is why it is the one left uncovered
 * rather than the one worth another instrument.
 *
 * Recorded on both themes because the outline is the subject and `borderDefault` is a different
 * value in each (`#627587` / `#748396`), measured 4.09 / 3.60 against `--base`.
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
        // `.tf.err` — the 1.5dp rust outline against the 1dp above it. Both weights in one frame
        // on purpose: the step is the signal, and a frame holding only the error cannot show it.
        AppTextField(value = "Romanian deadlift", onValueChange = {}, isError = true)
        // `.tf.multi` — the same box, taller. 96dp, which is `heightMd` doubled.
        AppTextField(value = "", onValueChange = {}, placeholder = "Note", singleLine = false)
        // Disabled — undrawn, kept, and photographed so "undrawn" does not mean "unpinned".
        AppTextField(value = "Disabled", onValueChange = {}, enabled = false)
    }
}
