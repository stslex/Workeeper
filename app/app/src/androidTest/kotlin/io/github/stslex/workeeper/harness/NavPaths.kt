// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput

/**
 * The UI paths the navigation regression suite walks, as named verbs. Everything reaches the app
 * through the semantics tree; `androidx.navigation` imports are banned here and detekt-enforced.
 */
@OptIn(ExperimentalTestApi::class)
internal class NavPaths(private val rule: ComposeTestRule) {

    // ----- primitives ---------------------------------------------------------------------------

    /** Wait for [tag] to exist, then assert it is displayed — not `waitForIdle`, which races. */
    fun awaitTag(tag: String) {
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), ARRIVAL_TIMEOUT_MS)
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    fun tap(tag: String) {
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), ARRIVAL_TIMEOUT_MS)
        rule.onNodeWithTag(tag).performClick()
    }

    /** [tap] for a control that may sit below the fold — Settings' rows are a scrolling column. */
    fun scrollToAndTap(tag: String) {
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), ARRIVAL_TIMEOUT_MS)
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    /** Tap the single node whose testTag starts with [prefix], for uuids minted at runtime. */
    fun tapTagStartingWith(prefix: String) {
        val matcher = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }
        rule.waitUntilAtLeastOneExists(matcher, ARRIVAL_TIMEOUT_MS)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    fun typeInto(tag: String, text: String) {
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), ARRIVAL_TIMEOUT_MS)
        rule.onNodeWithTag(tag).performTextInput(text)
    }

    /** Full testTag of the node starting with [prefix]; [useUnmergedTree] for merged-card tags. */
    fun tagStartingWith(prefix: String, useUnmergedTree: Boolean = false): String {
        val matcher = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }
        rule.waitUntil(ARRIVAL_TIMEOUT_MS) {
            rule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
        return rule.onAllNodes(matcher, useUnmergedTree).onFirst()
            .fetchSemanticsNode()
            .config[SemanticsProperties.TestTag]
    }

    /** Tags of every node starting with [prefix], after waiting for [atLeast] of them. */
    fun tagsStartingWith(prefix: String, atLeast: Int, useUnmergedTree: Boolean = false): List<String> {
        val matcher = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }
        rule.waitUntil(ARRIVAL_TIMEOUT_MS) {
            rule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().size >= atLeast
        }
        return rule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }
    }

    /** [assertTextEquals] against a node that only exists in the unmerged tree. */
    fun assertUnmergedText(tag: String, expected: String) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(expected)
    }

    /** Wait until [tag]'s text is no longer [previous] — a consumed navigation result. */
    fun awaitTextChangedFrom(tag: String, previous: String) {
        rule.waitUntil(ARRIVAL_TIMEOUT_MS) {
            val nodes = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            nodes.isNotEmpty() &&
                nodes.none { node ->
                    node.config.getOrNull(SemanticsProperties.Text)
                        ?.any { it.text == previous } == true
                }
        }
    }

    // ----- bottom-bar roots ---------------------------------------------------------------------

    fun toHome() = tap(BOTTOM_BAR_HOME)

    fun toAllTrainings() = tap(BOTTOM_BAR_TRAININGS)

    fun toAllExercises() = tap(BOTTOM_BAR_EXERCISES)

    // ----- journeys from Home -------------------------------------------------------------------

    fun openSettings() = tap("HomeSettingsButton")

    /** Archive is reached through Settings; there is no direct route from a bottom-bar root. */
    fun openArchiveFromSettings() = scrollToAndTap("SettingsArchiveRow")

    fun openExerciseChart() = tap("HomeChartsButton")

    fun openPastSession(sessionUuid: String) = tap("HomeRecentRow_$sessionUuid")

    /** Home -> start card -> picker -> "start blank". Needs no seeded rows. */
    fun startBlankSession() {
        tap("HomeStartButton")
        awaitTag("TrainingPickerSheet")
        tap("HomePickerStartBlankRow")
    }

    fun reEnterActiveSession() = tap("ActiveSessionBanner")

    // ----- journeys from the list roots ---------------------------------------------------------

    fun createExercise() = tap("AllExercisesFab")

    fun openExercise(exerciseUuid: String) = tap("AllExercisesItem_$exerciseUuid")

    fun createTraining() = tap("AllTrainingsFab")

    /** By name: `AllTrainingsItemName_<uuid>` sits inside a merged row and cannot be clicked. */
    fun openTraining(trainingName: String) {
        rule.waitUntilAtLeastOneExists(hasText(trainingName), ARRIVAL_TIMEOUT_MS)
        rule.onNodeWithText(trainingName).performClick()
    }

    // ----- journeys inside a live session -------------------------------------------------------

    /** Adds an exercise by creating one inline, which keeps the journey seed-free. */
    fun addInlineExerciseToSession(name: String) {
        tap("LiveWorkoutMenuButton")
        tap("SessionMenu_Add")
        awaitTag("ExercisePickerBottomSheet")
        typeInto("ExercisePickerQueryField", name)
        tap("ExercisePickerCreateCta")
    }

    /** The last two hops into the plan editor, from a session that already holds one exercise. */
    fun openPlanEditorForFirstExercise() {
        tapTagStartingWith("LiveExerciseCard_Menu_")
        tap("ExerciseMenu_EditPlan")
    }

    internal companion object {

        const val BOTTOM_BAR_HOME: String = "BottomAppBarItem_HOME"
        const val BOTTOM_BAR_TRAININGS: String = "BottomAppBarItem_TRAININGS"
        const val BOTTOM_BAR_EXERCISES: String = "BottomAppBarItem_EXERCISES"

        /** A failure budget, not an expected duration. */
        const val ARRIVAL_TIMEOUT_MS: Long = 5_000L
    }
}
