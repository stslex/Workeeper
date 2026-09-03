// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.database.snapshot.LiveDatabaseLocator
import io.github.stslex.workeeper.feature.recovery.diagnostics.StartupMigrationReporter
import java.io.File

/** Routing decision [StartupMigrationCoordinator] returns to `BaseApplication.onCreate`. */
sealed interface StartupCheck {

    /** Live db is at the current schema, or older with a registered migration path. */
    data object Proceed : StartupCheck

    /** Live db cannot be opened by this build; route to the Room-free `RecoveryActivity`. */
    data class RouteToRecovery(val reason: StartupMigrationFailureReason) : StartupCheck
}

/** Why the pre-flight is routing to `RecoveryActivity`. Drives Crashlytics keys + UI copy. */
enum class StartupMigrationFailureReason {

    /** Live db `user_version` exceeds [APP_DATABASE_VERSION]; Room does not downgrade. */
    APP_DOWNGRADE,

    /** Live db is older than [APP_DATABASE_VERSION] with no registered migration path. */
    NO_MIGRATION_PATH,

    /** Reading the live db file failed entirely — corruption, permissions, or not SQLite. */
    CANNOT_PEEK_LIVE_DB,

    /**
     * The peek said `Proceed` and the first Room open of the process threw anyway. A registered
     * migration that fails is the canonical case, but this covers the whole class:
     * [DatabaseSnapshotProvider.hasMigrationPath] answers "registered", never "succeeds".
     */
    LIVE_DB_OPEN_FAILED,
}

/**
 * Scenario 2 startup routing: peeks the live db schema and either proceeds or routes to the
 * Room-free `RecoveryActivity`. See documentation/feature-specs/backup-recovery.md.
 */
@SingleIn(AppScope::class)
class StartupMigrationCoordinator @Inject internal constructor(
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val liveDatabaseLocator: LiveDatabaseLocator,
    private val reporter: StartupMigrationReporter,
) {

    private val logger = Log.tag("StartupMigrationCoordinator")

    /** Most recent [checkAndRouteOrProceed] result; `null` until the check has run. */
    @Volatile
    var lastDecision: StartupCheck? = null
        private set

    @Volatile
    var lastRecoveryExportOutcome: RecoveryExportOutcome? = null
        private set

    suspend fun checkAndRouteOrProceed(): StartupCheck {
        val decision = computeDecision()
        lastDecision = decision
        return decision
    }

    /**
     * Records a route-to-recovery verdict for a failure the schema peek cannot see: the first Room
     * open of this process threw after [checkAndRouteOrProceed] returned [StartupCheck.Proceed].
     *
     * Recording is the whole point. Startup routing reads [lastDecision] and nothing else, so a
     * caller that merely catches the throw leaves `MainActivity` with a `Proceed` verdict over a
     * database this launch just proved unopenable — a silently broken app rather than a crash.
     * Treated exactly like a peek failure: the live file is preserved for the recovery export
     * before anything can mutate it, and the non-fatal carries the throwable.
     */
    suspend fun recordLiveDatabaseOpenFailure(error: Throwable): StartupCheck.RouteToRecovery {
        logger.w { "live database open failed after a Proceed peek: $error" }
        val decision = routeToRecovery(
            reason = StartupMigrationFailureReason.LIVE_DB_OPEN_FAILED,
            fromSchema = UNKNOWN_SCHEMA,
            liveDb = liveDatabaseLocator.liveDatabaseFile(),
            error = error,
        )
        lastDecision = decision
        return decision
    }

    private suspend fun computeDecision(): StartupCheck {
        val liveDb = liveDatabaseLocator.liveDatabaseFile()
        if (!liveDb.exists()) {
            // Fresh install — Room creates the db at the current schema on first DAO call.
            lastRecoveryExportOutcome = null
            snapshotProvider.deleteRecoveryExport()
            return StartupCheck.Proceed
        }

        val detectedVersion = when (val peek = snapshotProvider.peekSnapshotSchemaVersion(liveDb)) {
            is BackupResult.Success -> peek.data
            is BackupResult.Failure -> {
                logger.w { "peek failed at startup: ${peek.error}" }
                return routeToRecovery(
                    reason = StartupMigrationFailureReason.CANNOT_PEEK_LIVE_DB,
                    fromSchema = UNKNOWN_SCHEMA,
                    liveDb = liveDb,
                )
            }
        }

        return when {
            detectedVersion == APP_DATABASE_VERSION -> {
                lastRecoveryExportOutcome = null
                snapshotProvider.deleteRecoveryExport()
                StartupCheck.Proceed
            }

            detectedVersion > APP_DATABASE_VERSION -> routeToRecovery(
                reason = StartupMigrationFailureReason.APP_DOWNGRADE,
                fromSchema = detectedVersion,
                liveDb = liveDb,
            )

            snapshotProvider.hasMigrationPath(detectedVersion, APP_DATABASE_VERSION) -> {
                // Migration is registered; drop any stale Scenario-2 snapshot from a prior run.
                lastRecoveryExportOutcome = null
                snapshotProvider.deleteRecoveryExport()
                StartupCheck.Proceed
            }

            else -> routeToRecovery(
                reason = StartupMigrationFailureReason.NO_MIGRATION_PATH,
                fromSchema = detectedVersion,
                liveDb = liveDb,
            )
        }
    }

    private suspend fun routeToRecovery(
        reason: StartupMigrationFailureReason,
        fromSchema: Int,
        @Suppress("UnusedParameter") liveDb: File,
        error: Throwable? = null,
    ): StartupCheck.RouteToRecovery {
        // Best-effort preserve of the live db before anything can mutate it (WAL recovery).
        val preserved = runCatching { snapshotProvider.preserveDbBeforeMigration() }
        lastRecoveryExportOutcome = when (val result = preserved.getOrNull()) {
            is BackupResult.Success -> RecoveryExportOutcome.Available
            is BackupResult.Failure -> {
                logger.w { "recovery export unavailable: ${result.error}" }
                RecoveryExportOutcome.Failed
            }

            null -> {
                logger.e(
                    preserved.exceptionOrNull() ?: IllegalStateException("unknown export failure"),
                    "recovery export creation failed",
                )
                RecoveryExportOutcome.Failed
            }
        }
        reporter.recordStartupMigrationFailure(
            exception = error,
            fromSchema = fromSchema,
            toSchema = APP_DATABASE_VERSION,
            reason = reason,
        )
        return StartupCheck.RouteToRecovery(reason)
    }

    private companion object {
        /** Sentinel for `Crashlytics`/diagnostics when the peek itself failed. */
        const val UNKNOWN_SCHEMA = -1
    }
}
