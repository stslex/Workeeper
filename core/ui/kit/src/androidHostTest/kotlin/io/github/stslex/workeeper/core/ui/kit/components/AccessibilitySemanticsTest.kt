// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppDashedAddButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.rememberReorderableColumnState
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnItem
import io.github.stslex.workeeper.core.ui.kit.components.setbar.AppSetBar
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Semantics that goldens and handler tests cannot see: error state, labels, roles, targets.
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class AccessibilitySemanticsTest {

    @Test
    fun accessibilityPropertiesTheRebuildsCouldDrop() = runComposeUiTest {
        setContent {
            AppTheme {
                Column {
                    AppTextField(
                        modifier = Modifier.testTag(FIELD_ERRORED),
                        value = "",
                        onValueChange = {},
                        isError = true,
                    )
                    AppTextField(
                        modifier = Modifier.testTag(FIELD_CLEAN),
                        value = "Жим лёжа",
                        onValueChange = {},
                    )
                    AppTextField(
                        modifier = Modifier.testTag(FIELD_LABELLED),
                        value = "Подтягивания",
                        onValueChange = {},
                        accessibilityLabel = NAME_LABEL,
                    )
                    AppExerciseThumb(
                        modifier = Modifier.testTag(THUMB),
                        isWeighted = true,
                        onClick = {},
                        contentDescription = ADD_A_PHOTO,
                    )
                    AppDashedAddButton(
                        modifier = Modifier.testTag(DASHED_ADD),
                        text = ADD_EXERCISE,
                        onClick = {},
                    )
                    AppSetBar(
                        addLabel = SET_ADD,
                        removeLabel = SET_REMOVE,
                        onAdd = {},
                        onRemove = {},
                    )
                    // Three rows so first / middle / last are all present in one frame.
                    val reorderState = rememberReorderableColumnState { _, _ -> }
                    ROWS.forEachIndexed { index, tag ->
                        key(tag) {
                            Box(
                                modifier = Modifier
                                    .testTag(tag)
                                    .reorderableColumnItem(
                                        state = reorderState,
                                        key = tag,
                                        index = index,
                                        lastIndex = ROWS.lastIndex,
                                    ),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        assertAll(
            // TalkBack needs the error property; BasicTextField does not set it from isError.
            {
                onNodeWithTag(FIELD_ERRORED).assert(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Error),
                )
            },
            // The other direction, so the property is a signal and not a constant.
            {
                onNodeWithTag(FIELD_CLEAN).assert(
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Error),
                )
            },
            // The drawn label is a sibling node, so the field owes its own content description.
            {
                onNodeWithTag(FIELD_LABELLED).assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ContentDescription,
                        listOf(NAME_LABEL),
                    ),
                )
            },
            {
                onNodeWithTag(THUMB).assert(
                    SemanticsMatcher("has the click label «$ADD_A_PHOTO»") { node ->
                        node.config.getOrNull(SemanticsActions.OnClick)?.label == ADD_A_PHOTO
                    },
                )
            },
            // A reorder action that cannot happen must not be offered.
            { onNodeWithTag(ROWS.first()).assert(hasCustomActions(MOVE_DOWN)) },
            { onNodeWithTag(ROWS.first()).assert(hasNoCustomAction(MOVE_UP)) },
            { onNodeWithTag(ROWS.last()).assert(hasCustomActions(MOVE_UP)) },
            { onNodeWithTag(ROWS.last()).assert(hasNoCustomAction(MOVE_DOWN)) },
            // The middle row keeps both, so the filter is a boundary rule and not a blanket one.
            { onNodeWithTag(ROWS[1]).assert(hasCustomActions(MOVE_UP)) },
            { onNodeWithTag(ROWS[1]).assert(hasCustomActions(MOVE_DOWN)) },
            // A foundation `clickable` has no control type, so each owes an explicit Role.Button.
            { onNodeWithTag(DASHED_ADD).assert(hasRole(Role.Button)) },
            { onNodeWithTag(SET_BAR_ADD).assert(hasRole(Role.Button)) },
            { onNodeWithTag(SET_BAR_REMOVE).assert(hasRole(Role.Button)) },
            { onNodeWithTag(THUMB).assert(hasRole(Role.Button)) },
            // Negative control: a plain reorder Box is clickable-adjacent and is NOT a button.
            { onNodeWithTag(ROWS.first()).assert(hasNoRole(Role.Button)) },
            // A foundation `clickable` gets no minimum-target expansion; the thumb draws at 46dp.
            {
                val bounds = onNodeWithTag(THUMB).getUnclippedBoundsInRoot()
                val w = bounds.right - bounds.left
                val h = bounds.bottom - bounds.top
                assertTrue(
                    w >= MIN_TOUCH_TARGET && h >= MIN_TOUCH_TARGET,
                    "thumb target is $w × $h, under $MIN_TOUCH_TARGET",
                )
            },
        )
    }

    private fun hasCustomActions(label: String) = SemanticsMatcher("offers «$label»") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
    }

    private fun hasNoCustomAction(label: String) = SemanticsMatcher("does NOT offer «$label»") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().none { it.label == label }
    }

    private fun hasRole(role: Role) = SemanticsMatcher("is announced as $role") { node ->
        node.config.getOrNull(SemanticsProperties.Role) == role
    }

    private fun hasNoRole(role: Role) = SemanticsMatcher("is NOT announced as $role") { node ->
        node.config.getOrNull(SemanticsProperties.Role) != role
    }

    private companion object {

        const val FIELD_ERRORED = "field-errored"
        const val FIELD_CLEAN = "field-clean"
        const val THUMB = "thumb"
        const val ADD_A_PHOTO = "Add a photo"
        const val FIELD_LABELLED = "field-labelled"
        const val NAME_LABEL = "Название"
        const val MOVE_UP = "Move up"
        const val MOVE_DOWN = "Move down"
        const val DASHED_ADD = "dashed-add"
        const val ADD_EXERCISE = "Добавить упражнение"
        const val SET_ADD = "+ подход"
        const val SET_REMOVE = "− подход"

        /** [AppSetBar] tags its own halves, so the test reads them rather than re-tagging. */
        const val SET_BAR_ADD = "AppSetBarAdd"
        const val SET_BAR_REMOVE = "AppSetBarRemove"
        val ROWS = listOf("row-first", "row-middle", "row-last")

        /** Android's minimum interactive target. WCAG 2.5.5 asks 44; Material asks 48. */
        val MIN_TOUCH_TARGET = 48.dp
    }
}
