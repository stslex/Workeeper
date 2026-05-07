// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.dev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the navigation lifecycle refactor (PR #143).
 *
 * The bug class this guards against: a singleton-scoped controller-backed navigator
 * that retains the `NavHostController` across activity recreation. After the
 * activity is destroyed and re-created, the singleton survives but the retained
 * controller is stale — the next `Navigator.navTo(...)` call hits a detached
 * controller and either no-ops or crashes.
 *
 * The fixed architecture uses a controller-free singleton (`NavigatorEventBus`)
 * plus a composition-scoped executor (`NavigatorExt.NavigationEventBusSetup`) that
 * re-binds via `LaunchedEffect(navController)` on every recomposition. The bus
 * carries no `NavHostController` reference, so it cannot become stale.
 *
 * This test exercises the runtime invariant by recreating the host `MainActivity`
 * mid-flight and verifying that bottom-bar navigation still works on the
 * fresh-composition bridge. Combined with the JVM-level
 * `NavigationLifecycleRegressionTest` (in `app/app/src/test/...`), this gives end-to-end
 * coverage of the regression class.
 *
 * Scenarios NOT covered here (require DB seeding through Hilt + multi-screen
 * navigation infrastructure that does not yet exist in the test harness — tracked
 * in `documentation/tech-debt.md` → "Navigation lifecycle — RESOLVED in PR #143"
 * follow-ups):
 *  - PlanEditor save → previous screen reload exactly once (Exercise / SingleTraining /
 *    LiveWorkout consumers).
 *  - LiveWorkout finish session → replaceTo PastSession back-stack assertions.
 */
@Regression
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
internal class NavigationLifecycleRegressionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Activity recreation mid-flight must not break navigation. After the recreate
     * the new composition gets a fresh `NavController`; `NavigatorEventBus` is the
     * same singleton, but the executor (`NavigationEventBusSetup`) re-binds on the
     * new controller via `LaunchedEffect(navController)`. The next bottom-bar tap
     * routes through `NavigatorEventBus.navTo(...)` → fresh executor →
     * `navController.navigate(...)` and lands on the destination.
     */
    @Test
    fun navigation_works_after_activity_recreation() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()

        // Navigate to AllTrainings on the original activity instance to confirm the
        // pre-recreate executor wiring is functional.
        composeRule
            .onNodeWithTag("BottomAppBarItem_${BottomBarItem.TRAININGS.name}")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertIsDisplayed()

        // Recreate the activity. The Compose runtime drops the current composition;
        // a new one is created against the new MainActivity instance. The retained
        // ViewModels (e.g. AppRootViewModel and per-feature stores held by
        // NavBackStackEntry scopes) survive; the NavController is fresh.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // After recreation the AppRoot is visible again on the new composition.
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()

        // The decisive assertion: a navigation action through the singleton command
        // bus on the fresh executor lands on the target destination. If the bridge
        // had retained a stale NavController this tap would not reach a NavHost
        // destination.
        composeRule
            .onNodeWithTag("BottomAppBarItem_${BottomBarItem.EXERCISES.name}")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("AllExercisesGraph").assertIsDisplayed()
    }

    /**
     * A second pass to confirm the executor handles two navigation calls in a row
     * after recreation — guards against a "first nav after recreate works, second
     * does not" failure mode where the LaunchedEffect somehow re-binds incorrectly
     * after the first command.
     */
    @Test
    fun multiple_navigations_work_after_recreation() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("BottomAppBarItem_${BottomBarItem.TRAININGS.name}")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertIsDisplayed()

        composeRule
            .onNodeWithTag("BottomAppBarItem_${BottomBarItem.EXERCISES.name}")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllExercisesGraph").assertIsDisplayed()

        composeRule
            .onNodeWithTag("BottomAppBarItem_${BottomBarItem.HOME.name}")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
    }
}
