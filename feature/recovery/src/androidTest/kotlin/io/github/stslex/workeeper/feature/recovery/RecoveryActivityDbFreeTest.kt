// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the **DB-free invariant** for [RecoveryActivity]: from the moment
 * the activity is constructed through every visible-state lifecycle step
 * (CREATED → STARTED → RESUMED), no code path inside the activity's
 * collaborators may call a method that forces
 * `AppDatabase.openHelper.{writable,readable}Database`. That access would
 * trigger Room to open the live `.db` file — and on the Scenario 2 startup-
 * migration-failure path, this activity exists precisely BECAUSE that open
 * cannot succeed. Touching Room here would re-throw the migration failure
 * and prevent the user from reaching the recovery surface.
 *
 * Mechanism: [di.FailFastDatabaseModule] replaces the production
 * [io.github.stslex.workeeper.core.data.database.di.CoreDatabaseModule] with
 * a tripwire [io.github.stslex.workeeper.core.data.database.AppDatabase] whose
 * `openHelper` getter throws `AssertionError`. If any production code path
 * reachable from the activity calls `openHelper`, the assertion bubbles up
 * out of [ActivityScenario.launch] and the test fails.
 *
 * Spec: `documentation/feature-specs/backup-recovery.md` →
 * "RecoveryActivity location and DB-free invariant".
 *
 * Note on scope: this test asserts the **Hilt-graph + composition-time**
 * invariant. The four button callbacks (Update / Export raw / Report /
 * Export diagnostics) currently route only to file-path or pure-Kotlin
 * helpers; their behavior is implicitly covered because [moveToState]
 * walks the activity all the way to RESUMED — composition runs, every
 * `@Inject` lateinit is satisfied, the Compose tree renders. A future
 * contributor wiring a Room-dependent collaborator into the activity
 * surfaces here regardless of which button (if any) the user ends up
 * tapping.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
internal class RecoveryActivityDbFreeTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Test
    fun recoveryActivityLaunchesWithoutOpeningTheDatabase() {
        // ActivityScenario.launch resolves the Hilt graph (which is what
        // would force a Room-touching @Inject to fail) and walks the
        // activity through CREATED/STARTED/RESUMED — every lifecycle slot
        // a healthy launch produces. If the activity or any of its
        // collaborators touches `openHelper`, the tripwire throws
        // AssertionError and the launch fails.
        ActivityScenario.launch(RecoveryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }
}
