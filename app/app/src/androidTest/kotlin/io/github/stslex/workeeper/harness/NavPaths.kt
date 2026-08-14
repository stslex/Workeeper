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
 * The UI paths the navigation regression suite walks, as named verbs.
 *
 * Shared from the start because every class in this suite navigates before it asserts: route
 * reachability, store retention, result transport and back-stack restoration all need the same
 * handful of journeys. Written once here, they stay one journey; written per class, they become
 * four that drift.
 *
 * **Two channels only, by design.** Everything below reaches the app through the semantics tree.
 * Nothing in this file, or anywhere under `app/app/src/androidTest`, may import `androidx.navigation`
 * — that is what lets the same suite run unchanged across the Nav2 -> Nav3 swap, and it is enforced
 * by `:app:app:detektAndroidTestNavigation`. Row seeding is the other channel and lives in [NavSeed].
 *
 * Only the journeys actually walked are here. This is not a page-object layer.
 */
@OptIn(ExperimentalTestApi::class)
internal class NavPaths(private val rule: ComposeTestRule) {

    // ----- primitives ---------------------------------------------------------------------------

    /**
     * Wait for [tag] to exist, then assert it is displayed.
     *
     * [ComposeTestRule.waitForIdle] is deliberately NOT used for arrival. Three graphs gate their
     * tagged composable behind `if (state.isLoading) return` — `ExerciseGraph`, `SingleTrainingGraph`
     * and `PlanEditorGraph` — and the flag is cleared by an async database read. Idle can be reached
     * while that read is still outstanding, so `waitForIdle` returns before the tag exists and the
     * assertion fails on a screen that was merely about to appear. An explicit timeout is the honest
     * instrument: it either arrives inside the budget or the test has found something.
     */
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

    /**
     * Tap the single node whose testTag starts with [prefix].
     *
     * For per-entity tags whose uuid is minted at runtime rather than seeded — the live session's
     * `LiveExerciseCard_Menu_<performedExerciseUuid>` is the only one. Reading that uuid back out of
     * the database would work too, but it couples the path to a DAO shape for no gain: the session
     * under test holds exactly one exercise, so a prefix match is unambiguous.
     */
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

    /**
     * Resolve the full testTag of the single node whose tag starts with [prefix].
     *
     * The read-only counterpart to [tapTagStartingWith], for the same reason it exists: per-entity
     * tags whose uuid is minted at runtime. A caller that needs to assert on such a node twice —
     * before and after an action — needs the tag itself, not just a click.
     *
     * [useUnmergedTree] is required for any tag on a `Text` inside a card that merges its
     * descendants, which is the same trap `openTraining` documents: the tag exists, but not in the
     * tree `onNodeWithTag` queries by default. `LiveExerciseCardSub_<uuid>` is one — its card takes
     * a `Modifier.clickable` to expand, so the whole card is one merged node. Measured via a
     * semantics dump, not reasoned: the tag is absent from the merged tree and present in the
     * unmerged one.
     */
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

    /** [assertTextEquals] against a node that only exists in the unmerged tree. */
    fun assertUnmergedText(tag: String, expected: String) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(expected)
    }

    /**
     * Wait until [tag]'s text is no longer [previous].
     *
     * For asserting that a navigation result was consumed. The screen is already on-screen and
     * already has its text when the result arrives, so there is no node to wait for — the change
     * IS the signal, and it lands a recomposition after the Store reloads.
     *
     * Deliberately "changed away from" rather than "equals the expected new value": what is at
     * risk is whether the result arrived at all, and pinning the exact new text would additionally
     * couple this to a mapper's formatting, which has its own unit tests.
     *
     * Reads the unmerged tree for the reason [tagStartingWith] documents.
     */
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

    /**
     * Start a blank ad-hoc session: Home -> start card -> picker -> "start blank".
     *
     * Needs no seeded rows. `StartBlankRow` is composed before the picker's
     * `when { isLoading / empty / else }` branch, so it renders on an empty database, and the
     * session plus the training row behind it are minted by `CommonHandler.Init` on arrival.
     */
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

    /**
     * Open a seeded training by its name.
     *
     * By name and not by `AllTrainingsItemName_<uuid>`, which is the tag that exists for this row and
     * looks like the obvious selector. It is not usable for a click. `AppListRow` applies that tag to
     * the row's name `Text`, a descendant of a row that merges its descendants — and a merged parent
     * does not adopt a child's test tag, so the tag is absent from the merged tree that
     * `onNodeWithTag` queries, while the click action lives on the parent that has no tag of its own.
     * Measured, not reasoned: selecting by tag times out on a row that is on screen, and the same row
     * with the same timing responds to a text selector.
     *
     * The seeded name is unique per test, so this is unambiguous. If a row-level tag is ever added,
     * this is the one call site that changes.
     */
    fun openTraining(trainingName: String) {
        rule.waitUntilAtLeastOneExists(hasText(trainingName), ARRIVAL_TIMEOUT_MS)
        rule.onNodeWithText(trainingName).performClick()
    }

    // ----- journeys inside a live session -------------------------------------------------------

    /**
     * Add an exercise to the running session by creating one inline.
     *
     * Deliberately inline rather than picking a seeded row: the picker's create affordance is
     * offered whenever the typed name has no exact match, which on an empty database is always. That
     * keeps the whole LiveWorkout -> PlanEditor chain seed-free.
     */
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

        /**
         * Generous because it is a failure budget, not an expected duration: an arrival that needs a
         * database read plus a fade should land in well under a second on any device the suite runs
         * on. It is sized so that a genuinely stuck screen still fails inside a sane test runtime,
         * not so that a slow one squeaks through.
         */
        const val ARRIVAL_TIMEOUT_MS: Long = 5_000L
    }
}
