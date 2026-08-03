// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two accessibility properties this arc's rebuilds could drop without any other instrument
 * noticing, asserted where they can be read.
 *
 * **Why here and not in a golden or a handler test.** Both are semantics, not pixels and not state:
 * Paparazzi photographs a frame and never sees them, and a handler test never composes. §27's
 * standing rule is that anything whose evidence needs something other than one static frame owes a
 * direct assertion — this is that assertion for the two of them.
 *
 * **This suite is `workflow_dispatch`-only and does not gate the PR.** Named rather than left to be
 * discovered, on the same footing the loading-rule's composition branch is declared: an assertion
 * that exists and runs on demand is worth more than none, and worth less than a gate.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * An outline is the sighted half of "this field is wrong". `OutlinedTextField` set the other
     * half from `isError` itself; a field built on `BasicTextField` owes it explicitly, or TalkBack
     * announces an invalid box as an ordinary one while the reason sits visibly underneath it.
     */
    @Test
    fun erroredTextFieldExposesItsErrorState() {
        composeTestRule.setContent {
            AppTheme {
                AppTextField(
                    modifier = Modifier.testTag("field"),
                    value = "",
                    onValueChange = {},
                    isError = true,
                )
            }
        }

        composeTestRule.onNodeWithTag("field").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Error),
        )
    }

    /** The other direction, so the property is a signal and not a constant. */
    @Test
    fun cleanTextFieldExposesNoErrorState() {
        composeTestRule.setContent {
            AppTheme {
                AppTextField(
                    modifier = Modifier.testTag("field"),
                    value = "Жим лёжа",
                    onValueChange = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("field").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Error),
        )
    }

    /**
     * The empty thumb is a 46dp box whose only child is a decorative mark, and it replaced a
     * separately labelled button. Without a click label it is a control a screen-reader user
     * cannot discover, let alone identify — the mark says which TYPE the exercise is, which is not
     * what the tap does.
     */
    @Test
    fun emptyThumbAnnouncesWhatTheTapDoes() {
        composeTestRule.setContent {
            AppTheme {
                AppExerciseThumb(
                    modifier = Modifier.testTag("thumb"),
                    isWeighted = true,
                    onClick = {},
                    contentDescription = "Add a photo",
                )
            }
        }

        composeTestRule.onNodeWithTag("thumb").assert(
            SemanticsMatcher("has the click label") { node ->
                node.config.getOrNull(SemanticsActions.OnClick)?.label == "Add a photo"
            },
        )
    }
}
