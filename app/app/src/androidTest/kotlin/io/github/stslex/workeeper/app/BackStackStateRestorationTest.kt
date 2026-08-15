// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.harness.MetroTestRule
import io.github.stslex.workeeper.harness.NavPaths
import io.github.stslex.workeeper.harness.NavSeed
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Per-entry state and back-stack shape across leaving-and-returning. Guards the silent
 * regressions of per-entry `rememberSaveable` retention and back-stack restoration: nothing
 * crashes when either breaks — a list quietly re-opens at the top, a draft quietly vanishes, the
 * stack quietly collapses to its root on recreation.
 *
 * Four representative cases, not full coverage — the mechanism is shared:
 * 1. scroll position across a detail round trip (composition-local `rememberSaveable` state);
 * 2. an unsaved editor draft across a viewer round trip — which under the CURRENT behaviour is
 *    DISCARDED (a filed defect; see the test's KDoc), so the discard is what this oracle pins;
 * 3. selection mode across a bottom-bar tab round trip — which under the CURRENT navigator
 *    semantics arrives RESET (tab taps pop the current root inclusively and never restore), so
 *    reset is what this oracle pins;
 * 4. back-stack depth across activity recreation.
 *
 * The list case runs on AllExercises rather than Archive because Archive rows push to no detail
 * destination (`ArchivedItemRow` has no click) — a detail round trip is unreachable from there.
 * `LazyColumn`'s internal `rememberLazyListState()` takes the identical `rememberSaveable` path,
 * which is what is actually under test — entry retention, not the state declaration.
 */
@OptIn(ExperimentalTestApi::class)
@Regression
@RunWith(AndroidJUnit4::class)
internal class BackStackStateRestorationTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var paths: NavPaths
    private lateinit var seed: NavSeed

    @Before
    fun setUp() {
        paths = NavPaths(composeRule)
        seed = NavSeed(metroRule.appDatabase)
    }

    /**
     * SCROLL — a list scrolled deep, a detail opened from it, and the return lands where the user
     * left, not at the top. Seeds more rows than any sane viewport shows, so the scroll is a real
     * displacement.
     */
    @Test
    fun listScrollPositionSurvivesTheDetailRoundTrip() {
        val uuids = (1..SEEDED_ROWS).map { index ->
            seed.exercise("Restoration Probe %02d".format(index)).toString()
        }

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        // The rows composed on arrival are "the top"; the target is any seeded row that is not
        // among them, so the scroll to it is guaranteed to be a real displacement.
        val initiallyComposed = paths
            .tagsStartingWith(ROW_PREFIX, atLeast = 1)
            .toSet()
        val topRowTag = initiallyComposed.first()
        val targetTag = uuids.map { ROW_PREFIX + it }.first { it !in initiallyComposed }

        scrollListUntilComposed(targetTag)

        paths.tap(targetTag)
        paths.awaitTag(EXERCISE_GRAPH)
        paths.tap("ExerciseDetailBackButton")
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        // Restored scroll ⇒ the deep row is back in (or near) the viewport and the original top
        // row is no longer composed. A reset-to-top list inverts both.
        composeRule.waitUntilAtLeastOneExists(hasTestTag(targetTag), NavPaths.ARRIVAL_TIMEOUT_MS)
        composeRule.onNodeWithTag(targetTag).assertIsDisplayed()
        check(
            composeRule.onAllNodesWithTag(topRowTag).fetchSemanticsNodes().isEmpty(),
        ) { "The pre-scroll top row is composed again after the round trip — the list reset to the top." }
    }

    /**
     * DRAFT — pins the CURRENT semantics, which is a WIPE, not survival, and the pin is
     * deliberate. The Exercise Store re-fires `Action.Common.Init` on every composition re-entry
     * (`BaseStore.init` re-fires `initialActions`), and `applyLoaded` unconditionally overwrites
     * `name`/`description`/`tags` with database values — so an unsaved draft is DISCARDED by an
     * image-viewer round trip. Measured here, mechanism confirmed at `CommonHandler.applyLoaded`.
     *
     * This is a user-visible defect (same Init-refire family the LiveWorkout Store shields with
     * `withExpansionCarriedFrom`), filed in tech-debt.md. The oracle pins it anyway: stage 1.3 is
     * a behaviour-preserving swap, and a draft that suddenly SURVIVES the round trip under Nav3 is
     * as much an unexplained delta as one that vanishes elsewhere. When the defect is fixed, this
     * test is the one that goes red, and it gets updated WITH that fix — that is the pin working.
     */
    @Test
    fun editorDraftIsDiscardedByTheImageViewerRoundTrip() {
        val exerciseUuid = seed.exercise(
            name = "Draft Probe",
            imagePath = "/restoration-probe/image.jpg",
        )

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)
        paths.openExercise(exerciseUuid.toString())
        paths.awaitTag(EXERCISE_GRAPH)

        paths.tap("ExerciseEditButton")
        paths.awaitTag("ExerciseEditActionBar")
        paths.typeInto("ExerciseEditNameField", DRAFT_SUFFIX)

        // The draft really was typed — without this, a broken text input would make the
        // wipe assertion below pass vacuously.
        composeRule
            .onNodeWithTag("ExerciseEditNameField")
            .assertTextContains(value = DRAFT_SUFFIX, substring = true)

        paths.tap("ExerciseDescriptionImage")
        paths.awaitTag(IMAGE_VIEWER_GRAPH)
        paths.tap("ImageViewerBackButton")
        paths.awaitTag(EXERCISE_GRAPH)

        // The re-fired Init's reload is ASYNC: the field can recompose still holding the draft
        // for a few frames before applyLoaded overwrites it, so waiting for mere existence and
        // asserting immediately is a flaky-red race under load. Wait for the WIPE itself — the
        // draft text leaving the field — with the timeout as the failure budget: if the wipe
        // never lands (the pinned behaviour changed), this times out red.
        paths.awaitTextChangedFrom("ExerciseEditNameField", "Draft Probe$DRAFT_SUFFIX")
        composeRule
            .onNodeWithTag("ExerciseEditNameField")
            .assertTextContains(value = "Draft Probe", substring = false)
    }

    /**
     * SELECTION MODE — pins the CURRENT semantics, which is reset, not retention: a bottom-bar tab
     * tap pops the current root inclusively (`saveState` is written but nothing ever restores it),
     * so returning to the tab is a fresh entry with `SelectionMode.Off`. 1.3's replace-last must
     * arrive at the same observable — a selection that suddenly survives a tab round trip is as
     * much a regression as a draft that vanishes.
     */
    @Test
    fun selectionModeArrivesResetAfterABottomBarRoundTrip() {
        val exerciseUuid = seed.exercise("Selection Probe")

        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        composeRule.waitUntilAtLeastOneExists(
            hasTestTag(ROW_PREFIX + exerciseUuid),
            NavPaths.ARRIVAL_TIMEOUT_MS,
        )
        composeRule
            .onNodeWithTag(ROW_PREFIX + exerciseUuid)
            .performTouchInput { longClick() }
        paths.awaitTag("AllExercisesSelectionTopBar")

        paths.toHome()
        paths.awaitTag(HOME_GRAPH)
        paths.toAllExercises()
        paths.awaitTag(ALL_EXERCISES_GRAPH)

        paths.awaitTag("AllExercisesTopBar")
        check(
            composeRule.onAllNodesWithTag("AllExercisesSelectionTopBar").fetchSemanticsNodes().isEmpty(),
        ) { "Selection mode survived a bottom-bar round trip — tab taps no longer arrive reset." }
    }

    /**
     * DEPTH — the stack Home → Settings → Archive survives activity recreation: still on Archive
     * afterwards, and the back chain unwinds through Settings to Home. Under Nav2 the controller
     * saves this through the activity's SavedStateRegistry; under Nav3 it is the app-owned
     * `rememberNavBackStack` — either way, this observable.
     */
    @Test
    fun backStackDepthSurvivesActivityRecreation() {
        paths.awaitTag(HOME_GRAPH)
        paths.openSettings()
        paths.awaitTag(SETTINGS_GRAPH)
        paths.openArchiveFromSettings()
        paths.awaitTag(ARCHIVE_GRAPH)

        composeRule.activityRule.scenario.recreate()

        paths.awaitTag(ARCHIVE_GRAPH)
        paths.tap("ArchiveBackButton")
        paths.awaitTag(SETTINGS_GRAPH)
        paths.tap("SettingsBackButton")
        paths.awaitTag(HOME_GRAPH)
    }

    /**
     * Scroll the list until [tag] is composed — SEMANTICALLY, never by touch injection.
     *
     * Two prior versions of this journey used `performTouchInput { swipeUp() }`, and both went
     * red on CI's x86_64 emulator profile while green on arm64 (runs 31884113468, 31885121564).
     * The second failure falsified the pacing theory: 24 progress-checked swipes moved the
     * composed row window ZERO times — injected flings do not scroll this list on that profile
     * at all. `performScrollToNode` drives the lazy list's own scroll semantics instead, with no
     * gesture, no viewport math and no gesture-navigation interference. Its one precondition —
     * the item must be resolvable by the lazy layout — holds by construction: Paging's default
     * initial load (3 x pageSize = 30) covers all seeded rows up front, which also corrects this
     * journey's earlier framing — there is no paging append in it, and never was.
     */
    private fun scrollListUntilComposed(tag: String) {
        composeRule
            .onNodeWithTag("AllExercisesList")
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), NavPaths.ARRIVAL_TIMEOUT_MS)
    }

    private companion object {

        const val HOME_GRAPH = "HomeGraph"
        const val ALL_EXERCISES_GRAPH = "AllExercisesGraph"
        const val EXERCISE_GRAPH = "ExerciseGraph"
        const val IMAGE_VIEWER_GRAPH = "ImageViewerGraph"
        const val SETTINGS_GRAPH = "SettingsGraph"
        const val ARCHIVE_GRAPH = "ArchiveGraph"

        const val ROW_PREFIX = "AllExercisesItem_"
        const val DRAFT_SUFFIX = " Amended"

        /** More rows than any sane viewport composes at once — the scroll is a real displacement. */
        const val SEEDED_ROWS = 14
    }
}
