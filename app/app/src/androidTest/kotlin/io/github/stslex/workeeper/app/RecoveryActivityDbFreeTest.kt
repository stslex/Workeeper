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
import io.github.stslex.workeeper.feature.recovery.RecoveryScenario
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
    fun recoveryActivityScenariosLaunchWithoutOpeningTheDatabase() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val intents = listOf(
            RecoveryScenario.intent(application, RecoveryScenario.StartupMigration),
            RecoveryScenario.intent(application, RecoveryScenario.InterruptedRestore),
            RecoveryScenario.intent(
                context = application,
                scenario = RecoveryScenario.InterruptedRestore,
                allowContinue = true,
            ),
        )
        intents.forEach { recoveryIntent ->
            ActivityScenario.launch<RecoveryActivity>(
                recoveryIntent,
            ).use { activityScenario ->
                activityScenario.moveToState(Lifecycle.State.RESUMED)
                activityScenario.onActivity { activity -> activity.warmDeps() }
            }
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
