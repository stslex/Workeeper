// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.migration.hasMigrationPath
import io.github.stslex.workeeper.core.data.database.snapshot.LiveDatabaseLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/** Explicit, Room-free validation used only after the user requests Continue. */
interface InterruptedRestoreChecker {

    suspend fun check(): InterruptedRestoreCheckResult
}

sealed interface InterruptedRestoreCheckResult {

    data class Healthy(val userVersion: Int) : InterruptedRestoreCheckResult

    data class Unhealthy(
        val reason: Reason,
        /** Log/test detail only; UI maps [reason] to localized copy. */
        val detail: String? = null,
    ) : InterruptedRestoreCheckResult

    enum class Reason {
        LiveDatabaseMissing,
        IntegrityCheckFailed,
        UnsupportedSchema,
        CheckFailed,
    }
}

/** Framework-SQLite implementation; constructing it opens no database. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FrameworkInterruptedRestoreChecker internal constructor(
    private val liveDatabaseFile: () -> File,
    private val currentVersion: Int,
    private val migrationPathExists: (Int, Int) -> Boolean,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : InterruptedRestoreChecker {

    @Inject
    constructor(
        liveDatabaseLocator: LiveDatabaseLocator,
        @IODispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        liveDatabaseFile = liveDatabaseLocator::liveDatabaseFile,
        currentVersion = APP_DATABASE_VERSION,
        migrationPathExists = ::hasMigrationPath,
        dispatcher = dispatcher,
    )

    override suspend fun check(): InterruptedRestoreCheckResult = withContext(dispatcher) {
        val file = liveDatabaseFile()
        if (!file.isFile) {
            return@withContext InterruptedRestoreCheckResult.Unhealthy(
                InterruptedRestoreCheckResult.Reason.LiveDatabaseMissing,
            )
        }

        try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                val integrityRows = readIntegrityRows(database)
                if (integrityRows.isEmpty() || integrityRows.any { it != SQLITE_INTEGRITY_OK }) {
                    return@withContext InterruptedRestoreCheckResult.Unhealthy(
                        reason = InterruptedRestoreCheckResult.Reason.IntegrityCheckFailed,
                        detail = integrityRows.joinToString(separator = " | ").ifEmpty {
                            "integrity_check returned no rows"
                        },
                    )
                }

                val userVersion = readUserVersion(database)
                    ?: return@withContext InterruptedRestoreCheckResult.Unhealthy(
                        reason = InterruptedRestoreCheckResult.Reason.CheckFailed,
                        detail = "user_version returned no row",
                    )
                val supported = userVersion == currentVersion ||
                    migrationPathExists(userVersion, currentVersion)
                if (!supported) {
                    return@withContext InterruptedRestoreCheckResult.Unhealthy(
                        reason = InterruptedRestoreCheckResult.Reason.UnsupportedSchema,
                        detail = "$userVersion->$currentVersion",
                    )
                }
                InterruptedRestoreCheckResult.Healthy(userVersion)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: SQLiteDatabaseCorruptException) {
            InterruptedRestoreCheckResult.Unhealthy(
                reason = InterruptedRestoreCheckResult.Reason.IntegrityCheckFailed,
                detail = error.message ?: error::class.java.name,
            )
        } catch (error: SQLiteException) {
            InterruptedRestoreCheckResult.Unhealthy(
                reason = InterruptedRestoreCheckResult.Reason.CheckFailed,
                detail = error.message ?: error::class.java.name,
            )
        } catch (error: SecurityException) {
            InterruptedRestoreCheckResult.Unhealthy(
                reason = InterruptedRestoreCheckResult.Reason.CheckFailed,
                detail = error.message ?: error::class.java.name,
            )
        }
    }

    /** Consume every row: any non-`ok` row invalidates the whole check. */
    private fun readIntegrityRows(database: SQLiteDatabase): List<String> =
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0).orEmpty())
                }
            }
        }

    private fun readUserVersion(database: SQLiteDatabase): Int? =
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private companion object {
        const val SQLITE_INTEGRITY_OK = "ok"
    }
}
