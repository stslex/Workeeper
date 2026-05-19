// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.feature.recovery.diagnostics.StartupMigrationReporter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discriminator for the routing decision the [StartupMigrationCoordinator]
 * returns to `BaseApplication.onCreate`.
 */
sealed interface StartupCheck {

    /**
     * Live database is at the current code's schema, or at an older schema
     * with a registered migration path. `MainActivity` opens normally; Room
     * applies any pending migration lazily on first DAO access.
     */
    data object Proceed : StartupCheck

    /**
     * Live database cannot be opened by this build. `MainActivity` must
     * finish itself and launch `RecoveryActivity` instead so the user has
     * a Room-free path to export their data or update the app.
     */
    data class RouteToRecovery(val reason: StartupMigrationFailureReason) : StartupCheck
}

/** Why the pre-flight is routing to `RecoveryActivity`. Drives Crashlytics keys + UI copy. */
enum class StartupMigrationFailureReason {

    /**
     * Live db's `user_version` is higher than [APP_DATABASE_VERSION] — the
     * user installed a build that is older than the one that wrote their
     * data. Room does not support downgrades, and the data is intact on
     * disk — only the app build needs updating.
     */
    APP_DOWNGRADE,

    /**
     * Live db's `user_version` is older than [APP_DATABASE_VERSION] and
     * `hasMigrationPath` returns false — a developer shipped a schema bump
     * without registering the matching `Migration` object. The
     * `MigrationsRegistryTest` should have caught this pre-merge; reaching
     * this branch in production is itself a bug worth recording.
     */
    NO_MIGRATION_PATH,

    /**
     * Reading the live db file failed entirely — corruption, permissions,
     * or the file is not SQLite. Routes the user to recovery so they can
     * at least export the raw bytes (the file's still on disk).
     */
    CANNOT_PEEK_LIVE_DB,
}

/**
 * Pre-flight Scenario 2 (startup) routing. Called from
 * `BaseApplication.onCreate` **only** when `restore_in_progress` is false
 * (the Scenario 1 path handles in-progress restores via
 * [RestoreRecoveryCoordinator]).
 *
 * Algorithm — **trust the registered migration plan**:
 * 1. Peek the live db's `PRAGMA user_version` via the Room-free
 *    `SQLiteDatabase.openDatabase` (no Room init, no migration trigger).
 * 2. Compare with [APP_DATABASE_VERSION] and consult
 *    [DatabaseSnapshotProvider.hasMigrationPath].
 * 3. Either proceed (Room handles migration lazily later) or route to
 *    recovery (preserve the live db, report a non-fatal, ask
 *    `MainActivity` to finish + launch `RecoveryActivity`).
 *
 * The "trust the plan" path is a deliberate trade-off over the spec's
 * literal reading (which would also try to open Room and catch the
 * exception). Reasons:
 * - PR-B's `MigrationsRegistryTest` catches *missing* migrations pre-merge
 *   — the realistic Scenario 2 case in production is "schema bumped
 *   without the migration", which the path-missing branch catches.
 * - A registered-but-buggy migration would still surface as a Room
 *   exception at first DAO call — same crash as today, narrower failure
 *   mode than the missing-migration case.
 * - Avoids the resource-leak risk of a half-opened Room instance and the
 *   startup cost of triggering migration in the pre-flight path.
 *
 * Snapshot timing: we only preserve `cache/pre_migration_backup.db` on
 * the [StartupCheck.RouteToRecovery] branch. The live db is pristine at
 * that point (Room never opened it), so the snapshot captures the same
 * bytes the spec asks for, without paying the copy cost on every normal
 * launch.
 *
 * Spec: `documentation/feature-specs/backup-recovery.md` → "Scenario 2".
 */
@Singleton
class StartupMigrationCoordinator @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val snapshotProvider: DatabaseSnapshotProvider,
    private val reporter: StartupMigrationReporter,
) {

    private val logger = Log.tag("StartupMigrationCoordinator")

    /**
     * The most recent [checkAndRouteOrProceed] result, cached so
     * `MainActivity.onCreate` can read it without re-running the pre-flight
     * (the check ran once already in `BaseApplication.onCreate`). `null`
     * means the check has not run yet on this process.
     */
    @Volatile
    var lastDecision: StartupCheck? = null
        private set

    suspend fun checkAndRouteOrProceed(): StartupCheck {
        val decision = computeDecision()
        lastDecision = decision
        return decision
    }

    private suspend fun computeDecision(): StartupCheck {
        val liveDb = context.getDatabasePath(AppDatabase.NAME)
        if (!liveDb.exists()) {
            // Fresh install — no database file yet. Room creates one at the
            // current schema on first DAO call. Nothing to recover.
            snapshotProvider.deletePreMigrationBackup()
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
                snapshotProvider.deletePreMigrationBackup()
                StartupCheck.Proceed
            }

            detectedVersion > APP_DATABASE_VERSION -> routeToRecovery(
                reason = StartupMigrationFailureReason.APP_DOWNGRADE,
                fromSchema = detectedVersion,
                liveDb = liveDb,
            )

            snapshotProvider.hasMigrationPath(detectedVersion, APP_DATABASE_VERSION) -> {
                // Migration is registered; Room will apply it on first DAO
                // access. Drop any stale Scenario-2 snapshot from a previous
                // launch so the cache slot stays empty in steady state.
                snapshotProvider.deletePreMigrationBackup()
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
    ): StartupCheck.RouteToRecovery {
        // Preserve the live db before MainActivity can attempt anything that
        // might mutate the file (e.g. WAL recovery on Room open). Best
        // effort — if preserve fails, log and continue to recovery anyway;
        // the user still gets the Update / Report buttons.
        val preserved = snapshotProvider.preserveDbBeforeMigration()
        if (preserved == null) {
            logger.w { "preserveDbBeforeMigration returned null; recovery export will be empty" }
        }
        reporter.recordStartupMigrationFailure(
            exception = null,
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
