// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity
import io.github.stslex.workeeper.harness.MetroTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The DB-free invariant for [RecoveryActivity]: launching it and resolving its collaborators must
 * open no `SQLiteConnection`. See documentation/feature-specs/backup-recovery.md.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class RecoveryActivityDbFreeTest {

    @get:Rule
    val metroRule = MetroTestRule(
        appDatabaseFactory = { failFastDatabase() },
    )

    @Test
    fun recoveryActivityLaunchesWithoutOpeningTheDatabase() {
        ActivityScenario.launch(RecoveryActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            // GUARD: the lifecycle walk never reads the `by lazy` deps — warmDeps is what
            // forces both collaborators through their constructors inside the tripwire window.
            scenario.onActivity { activity -> activity.warmDeps() }
        }
    }

    private companion object {

        private const val TRIPWIRE_MESSAGE =
            "DB-free invariant violated: RecoveryActivity must not open the database " +
                "(no SQLiteConnection to the live DB permitted during the activity's lifecycle)"

        /**
         * A real [AppDatabase] on a [SQLiteDriver] whose `open()` throws. Room connects lazily, so
         * building the graph does not trip it — only a forbidden DB access does.
         */
        fun failFastDatabase(): AppDatabase = Room
            .inMemoryDatabaseBuilder<AppDatabase>(
                ApplicationProvider.getApplicationContext<Application>(),
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
