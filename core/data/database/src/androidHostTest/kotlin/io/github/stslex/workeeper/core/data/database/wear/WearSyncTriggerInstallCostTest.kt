// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The other half of the trigger-repair contract: a database that is already healthy must be left
 * alone. Repairing drift is only correct if it is not a rewrite on every launch.
 *
 * This counts the DDL the preparation actually issues, recorded at the SQLite driver, because the
 * obvious assertion — that the bodies still match canonical — passes whether or not preparation
 * dropped and recreated all twelve, and so proves nothing about cost.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class WearSyncTriggerInstallCostTest {

    private val statements = mutableListOf<String>()
    private lateinit var database: AppDatabase

    @BeforeEach
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(RecordingDriver(AndroidSQLiteDriver(), statements))
            .allowMainThreadQueries()
            .build()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `preparing an already-healthy database drops nothing and recreates nothing`() = runTest {
        // The counter's own known-positive. Without it, "zero drops" and "the recorder never saw
        // anything" are the same observation, and the test would pass with the recorder unwired.
        prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
        assertEquals(
            WEAR_SYNC_TRIGGERS.size,
            statements.count { it.startsWith(DROP_PREFIX) },
            "installing on an empty database must issue one DROP per canonical trigger",
        )
        assertEquals(
            WEAR_SYNC_TRIGGERS.size,
            statements.count { it.startsWith(CREATE_PREFIX) },
            "installing on an empty database must issue one CREATE per canonical trigger",
        )

        statements.clear()
        prepareWearSyncStorage(database, rotateDatabaseEpoch = false)

        assertEquals(
            0,
            statements.count { it.startsWith(DROP_PREFIX) },
            "a healthy database must not be dropped: ${statements.filter { it.startsWith(DROP_PREFIX) }}",
        )
        assertEquals(
            0,
            statements.count { it.startsWith(CREATE_PREFIX) },
            "a healthy database must not be recreated: " +
                "${statements.filter { it.startsWith(CREATE_PREFIX) }}",
        )
    }

    /**
     * Records every statement the preparation compiles. Every write goes through
     * `SQLiteConnection.prepare`, including `executeSQL`, so this sees the DDL without the
     * production code knowing it is being watched.
     */
    private class RecordingDriver(
        private val delegate: SQLiteDriver,
        private val statements: MutableList<String>,
    ) : SQLiteDriver {

        override fun open(fileName: String): SQLiteConnection =
            RecordingConnection(delegate.open(fileName), statements)
    }

    private class RecordingConnection(
        private val delegate: SQLiteConnection,
        private val statements: MutableList<String>,
    ) : SQLiteConnection by delegate {

        override fun prepare(sql: String): SQLiteStatement {
            statements += sql
            return delegate.prepare(sql)
        }
    }

    private companion object {
        const val DROP_PREFIX = "DROP TRIGGER"
        const val CREATE_PREFIX = "CREATE TRIGGER"
    }
}
