// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity
import io.github.stslex.workeeper.harness.MetroTestRule
import io.mockk.every
import io.mockk.mockk
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
 * Mechanism (App-Scope Collapse Step 6, Phase 3.3): relocated from
 * `feature/recovery` into the consolidated `:app:app` androidTest suite. The
 * former Hilt `FailFastDatabaseModule` (which `@TestInstallIn`-replaced
 * `CoreDatabaseModule`) is replaced by a [MetroTestRule] `appDatabase` **root
 * override** — a tripwire [AppDatabase] whose `openHelper` getter throws
 * `AssertionError`. `RecoveryActivity` resolves `databaseSnapshotProvider` /
 * `recoveryDiagnosticsExporter` from the per-test Metro graph via
 * `context.appGraphContract()`; the graph's DB-cascade derives the DAOs from
 * this one root, so if any production code path reachable from the activity
 * calls `openHelper`, the assertion bubbles up out of [ActivityScenario.launch]
 * and the test fails.
 *
 * Spec: `documentation/feature-specs/backup-recovery.md` →
 * "RecoveryActivity location and DB-free invariant".
 *
 * Note on scope: this test asserts the **graph + composition-time** invariant.
 * The four button callbacks (Update / Export raw / Report / Export diagnostics)
 * currently route only to file-path or pure-Kotlin helpers; their behavior is
 * implicitly covered because [ActivityScenario.moveToState] walks the activity
 * all the way to RESUMED — composition runs, both app-graph-resolved
 * collaborators are constructed, the Compose tree renders. A future contributor
 * wiring a Room-dependent collaborator into the activity surfaces here
 * regardless of which button (if any) the user ends up tapping.
 */
@RunWith(AndroidJUnit4::class)
internal class RecoveryActivityDbFreeTest {

    @get:Rule
    val metroRule = MetroTestRule(
        appDatabase = { failFastDatabase() },
    )

    @Test
    fun recoveryActivityLaunchesWithoutOpeningTheDatabase() {
        // ActivityScenario.launch resolves the app graph (which is what would
        // force a Room-touching read to fail) and walks the activity through
        // CREATED/STARTED/RESUMED — every lifecycle slot a healthy launch
        // produces. If the activity or any of its collaborators touches
        // `openHelper`, the tripwire throws AssertionError and the launch fails.
        ActivityScenario.launch(RecoveryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    private companion object {

        private const val TRIPWIRE_MESSAGE =
            "DB-free invariant violated: RecoveryActivity must not trigger Room init " +
                "(no openHelper access permitted during the activity's lifecycle)"

        /**
         * A relaxed-mock [AppDatabase] whose `openHelper` getter throws. Relaxed so the graph's
         * DB-cascade (which derives the 9 DAOs + `DbTransitionRunner` from this root) resolves; the
         * activity should never reach a DAO, and must never force `openHelper`.
         */
        fun failFastDatabase(): AppDatabase {
            val mock = mockk<AppDatabase>(relaxed = true)
            every { mock.openHelper } throws AssertionError(TRIPWIRE_MESSAGE)
            return mock
        }
    }
}
