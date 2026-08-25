// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import io.github.stslex.workeeper.core.ui.kit.R as KitR

/**
 * The field name must reach TalkBack through `AppNumberInput.accessibilityLabel`, since the
 * column header is not associated with it. GUARD: one composition only — a second hangs.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class LiveSetRowSemanticsTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fieldsAnnounceTheirUnit() = runComposeUiTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val weightLabel = context.getString(KitR.string.core_ui_kit_set_field_a11y_weight)
        val repsLabel = context.getString(KitR.string.core_ui_kit_set_field_a11y_reps)
        setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    LiveSetRow(
                        set = set(),
                        isWeighted = true,
                        onWeightChange = {},
                        onRepsChange = {},
                        onTypeChange = {},
                        onMarkDone = {},
                        onUncheck = {},
                        editable = true,
                    )
                    LiveSetRow(
                        set = set().copy(weight = null, reps = 12),
                        isWeighted = false,
                        onWeightChange = {},
                        onRepsChange = {},
                        onTypeChange = {},
                        onMarkDone = {},
                        onUncheck = {},
                        editable = true,
                    )
                }
            }
        }
        assertAll(
            { onNodeWithContentDescription(weightLabel).assertExists() },
            { onAllNodesWithContentDescription(repsLabel).assertCountEquals(2) },
        )
    }

    private fun set(): LiveSetUiModel = LiveSetUiModel(
        position = 0,
        weight = 100.0,
        reps = 5,
        type = SetTypeUiModel.WORK,
        isDone = false,
    )
}
