// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.lifecycle.Lifecycle
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity
import io.github.stslex.workeeper.harness.MetroTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the **DB-free invariant** for [RecoveryActivity]: from the moment
 * the activity is constructed through every visible-state lifecycle step
 * (CREATED → STARTED → RESUMED), no code path inside the activity's
 * collaborators may open a `SQLiteConnection` to the live database. That access
 * would trigger Room to open the live `.db` file — and on the Scenario 2 startup-
 * migration-failure path, this activity exists precisely BECAUSE that open
 * cannot succeed. Touching Room here would re-throw the migration failure
 * and prevent the user from reaching the recovery surface.
 *
 * Mechanism (App-Scope Collapse Step 6, Phase 3.3; Room 3 port): relocated from
 * `feature/recovery` into the consolidated `:app:app` androidTest suite. The
 * former Hilt `FailFastDatabaseModule` (which `@TestInstallIn`-replaced
 * `CoreDatabaseModule`) is replaced by a [MetroTestRule] `appDatabase` **root
 * override** — a real [AppDatabase] built with a [SQLiteDriver] whose `open()`
 * throws `AssertionError` (Room 3 removed `openHelper`; the driver is now the
 * connection seam). `RecoveryActivity` resolves `databaseSnapshotProvider` /
 * `recoveryDiagnosticsExporter` from the per-test Metro graph via its typed
 * holder (`RecoveryDepsHolder`); the graph's DB-cascade derives the DAOs from
 * this one root, so if any production code path reachable from the activity
 * opens a connection, the assertion bubbles up out of [ActivityScenario.launch]
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
        appDatabaseFactory = { failFastDatabase() },
    )

    @Test
    fun recoveryActivityLaunchesWithoutOpeningTheDatabase() {
        // ActivityScenario.launch resolves the app graph (which is what would
        // force a Room-touching read to fail) and walks the activity through
        // CREATED/STARTED/RESUMED — every lifecycle slot a healthy launch
        // produces. If the activity or any of its collaborators touches
        // a SQLiteConnection, the tripwire driver throws AssertionError and the launch fails.
        ActivityScenario.launch(RecoveryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    private companion object {

        private const val TRIPWIRE_MESSAGE =
            "DB-free invariant violated: RecoveryActivity must not open the database " +
                "(no SQLiteConnection to the live DB permitted during the activity's lifecycle)"

        /**
         * A REAL [AppDatabase] built with a [SQLiteDriver] whose `open()` throws. This is
         * STRONGER than the former relaxed-mock-`openHelper` tripwire (Room 3 removed `openHelper`,
         * so there is no getter to mock): the mock proved "nothing touched the DB *object*" — a
         * proxy, since a mock can never open a connection anyway — whereas a throwing driver proves
         * the invariant itself, "no *connection* was opened to the live database", against a real
         * `AppDatabase` wired exactly as production (`Room.inMemoryDatabaseBuilder(...).setDriver(...)`).
         * Room opens the connection lazily on first DAO/pragma access, so building the graph does not
         * trip it; only a forbidden DB access — which this activity must never make — calls
         * `driver.open()` and throws [TRIPWIRE_MESSAGE].
         */
        fun failFastDatabase(): AppDatabase = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            )
            .setDriver(ThrowingSQLiteDriver)
            .allowMainThreadQueries()
            .build()

        /** A [SQLiteDriver] that refuses to open any connection — the DB-free tripwire. */
        private object ThrowingSQLiteDriver : SQLiteDriver {
            override fun open(fileName: String): SQLiteConnection =
                throw AssertionError(TRIPWIRE_MESSAGE)
        }
    }
}
