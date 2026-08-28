// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_empty_hint
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.resources.core_ui_plan_editor_read_plan_empty
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.stslex.workeeper.core.ui.kit.resources.Res as KitRes
import io.github.stslex.workeeper.core.ui.plan_editor.resources.Res as PlanEditorRes

class PlanEditorSceneIosTest {

    @Test
    fun readOnlyCopyAndEditableAddRenderAndDispatch() = runComposeUiTest {
        val editable = mutableStateOf(false)
        val actions = mutableListOf<PlanEditorBodyAction>()
        val emptyPlan = persistentListOf<PlanSetUiModel>()

        setContent {
            AppTheme {
                if (editable.value) {
                    PlanEditorBody(
                        draft = emptyPlan,
                        isWeighted = true,
                        onAction = actions::add,
                        scrollable = false,
                    )
                } else {
                    PlanSetCard(
                        plan = emptyPlan,
                        isWeighted = true,
                        onAction = null,
                    )
                }
            }
        }

        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()

        val readOnlyEmpty = runBlocking {
            getString(PlanEditorRes.string.core_ui_plan_editor_read_plan_empty)
        }
        onNodeWithText(readOnlyEmpty).assertIsDisplayed()
        onNodeWithTag("PlanEditorBodyEmpty").assertIsDisplayed()

        editable.value = true
        waitForIdle()

        val editableEmpty = runBlocking {
            getString(KitRes.string.core_ui_kit_plan_editor_empty_hint)
        }
        onNodeWithText(editableEmpty).assertIsDisplayed()
        onNodeWithTag("AppSetBarAdd").assertIsDisplayed()
        assertEquals(emptyList<PlanEditorBodyAction>(), actions)

        onNodeWithTag("AppSetBarAdd").performClick()
        assertEquals(listOf<PlanEditorBodyAction>(PlanEditorBodyAction.OnAddSet), actions)
    }
}
