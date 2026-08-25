// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.database.sqlite.SQLiteDatabase
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.RandomAccessFile
import java.nio.file.Path

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
internal class InterruptedRestoreCheckerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `real healthy SQLite file passes integrity and current-version checks`() = runTest {
        val file = createDatabase(version = APP_DATABASE_VERSION)
        val checker = checker(file)

        assertEquals(
            InterruptedRestoreCheckResult.Healthy(APP_DATABASE_VERSION),
            checker.check(),
        )
    }

    @Test
    fun `real SQLite btree corruption fails the full integrity check`() = runTest {
        val file = createDatabase(version = APP_DATABASE_VERSION)
        RandomAccessFile(file, "rw").use { random ->
            // Page one remains a valid SQLite header, so open succeeds. Page two is the user-table
            // root; an invalid btree page type makes PRAGMA integrity_check report corruption.
            random.seek(SQLITE_PAGE_SIZE.toLong())
            random.writeByte(0)
        }
        val checker = checker(file)

        assertEquals(
            InterruptedRestoreCheckResult.Reason.IntegrityCheckFailed,
            (checker.check() as InterruptedRestoreCheckResult.Unhealthy).reason,
        )
    }

    @Test
    fun `healthy older schema with a registered migration path is accepted`() = runTest {
        val olderVersion = APP_DATABASE_VERSION - 1
        val file = createDatabase(version = olderVersion)
        val checker = checker(
            file = file,
            migrationPathExists = { from, to ->
                from == olderVersion && to == APP_DATABASE_VERSION
            },
        )

        assertEquals(
            InterruptedRestoreCheckResult.Healthy(olderVersion),
            checker.check(),
        )
    }

    @Test
    fun `healthy database with no supported migration path is rejected`() = runTest {
        val unsupportedVersion = APP_DATABASE_VERSION + 1
        val file = createDatabase(version = unsupportedVersion)
        val checker = checker(file)

        assertEquals(
            InterruptedRestoreCheckResult.Unhealthy(
                reason = InterruptedRestoreCheckResult.Reason.UnsupportedSchema,
                detail = "$unsupportedVersion->$APP_DATABASE_VERSION",
            ),
            checker.check(),
        )
    }

    private fun checker(
        file: java.io.File,
        migrationPathExists: (Int, Int) -> Boolean = { from, to -> from == to },
    ) = FrameworkInterruptedRestoreChecker(
        liveDatabaseFile = { file },
        currentVersion = APP_DATABASE_VERSION,
        migrationPathExists = migrationPathExists,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun createDatabase(version: Int): java.io.File {
        val file = tempDir.resolve("database_$version.db").toFile()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("PRAGMA page_size=$SQLITE_PAGE_SIZE")
            database.execSQL("CREATE TABLE recovery_probe(id INTEGER PRIMARY KEY, value TEXT)")
            database.execSQL("INSERT INTO recovery_probe(value) VALUES ('healthy')")
            database.version = version
        }
        return file
    }

    private companion object {
        const val SQLITE_PAGE_SIZE = 4096
    }
}
