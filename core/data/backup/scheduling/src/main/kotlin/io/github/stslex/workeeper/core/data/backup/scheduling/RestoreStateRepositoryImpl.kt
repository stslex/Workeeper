// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [RestoreStateRepository] over its own `restore_state_prefs` file.
 * GUARD: key names are wire format - never rename without a deprecation path.
 */
// GUARD: mint the store via DataStoreProviderFactory only. See documentation/tech-debt.md.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RestoreStateRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
) : RestoreStateRepository {

    @Inject
    constructor(storeFactory: DataStoreProviderFactory) : this(
        storeFactory.create(PREFS_NAME).dataStore,
    )

    override suspend fun beginAttempt(attempt: RestoreAttempt): Boolean {
        require(attempt.phase == RestoreAttempt.Phase.Prepared) {
            "an attempt enters the slot as Prepared, not ${attempt.phase}"
        }
        require(
            attempt.kind != RestoreAttempt.Kind.Rollback || attempt.rollbackOrigin != null,
        ) { "a rollback must journal its origin; the replay reads it to pick the user's dialog" }
        var claimed = false
        dataStore.edit { prefs ->
            val ownerId = prefs[KEY_ATTEMPT_ID]
            val legacyMarker = prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] == true
            // Same-id reclaim is idempotent; a legacy marker belongs only to its synthetic owner.
            val ownsSlot = when {
                ownerId != null -> ownerId == attempt.id
                legacyMarker -> attempt.id == LEGACY_ATTEMPT_ID
                else -> true
            }
            if (!ownsSlot) {
                claimed = false
                return@edit
            }
            prefs[KEY_ATTEMPT_ID] = attempt.id
            prefs[KEY_ATTEMPT_KIND] = attempt.kind.name
            prefs[KEY_ATTEMPT_PHASE] = RestoreAttempt.Phase.Prepared.name
            // Convert legacy state to the owner-scoped record in this atomic edit.
            prefs.remove(KEY_LEGACY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_LEGACY_MUTATION_INTERRUPTED)
            attempt.rollbackSnapshotPath
                ?.let { prefs[KEY_ATTEMPT_ROLLBACK_PATH] = it }
                ?: prefs.remove(KEY_ATTEMPT_ROLLBACK_PATH)
            // Put/remove, never put-only: a same-id re-claim must not inherit a stale origin.
            attempt.rollbackOrigin
                ?.let { prefs[KEY_ATTEMPT_ROLLBACK_ORIGIN] = it.name }
                ?: prefs.remove(KEY_ATTEMPT_ROLLBACK_ORIGIN)
            attempt.context?.let { context ->
                prefs[KEY_BACKUP_SCHEMA_VERSION] = context.backupSchemaVersion
                prefs[KEY_BACKUP_CREATED_AT] = context.backupCreatedAtEpochMs
                prefs[KEY_BACKUP_APP_VERSION] = context.backupAppVersion
                prefs[KEY_RESTORE_STARTED_AT] = context.startedAtEpochMs
            }
            claimed = true
        }
        return claimed
    }

    override suspend fun recordAttemptCommitted(attemptId: String): Boolean {
        var advanced = false
        dataStore.edit { prefs ->
            if (prefs[KEY_ATTEMPT_ID] != attemptId) return@edit
            prefs[KEY_ATTEMPT_PHASE] = RestoreAttempt.Phase.Committed.name
            advanced = true
        }
        return advanced
    }

    override suspend fun resolveAttempt(attemptId: String): Boolean {
        var resolved = false
        dataStore.edit { prefs ->
            val ownerId = prefs[KEY_ATTEMPT_ID]
            // Only the synthetic legacy owner may resolve an id-less marker.
            val ownsLegacy = ownerId == null &&
                prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] == true &&
                attemptId == LEGACY_ATTEMPT_ID
            if (ownerId != attemptId && !ownsLegacy) return@edit
            prefs.remove(KEY_ATTEMPT_ID)
            prefs.remove(KEY_ATTEMPT_KIND)
            prefs.remove(KEY_ATTEMPT_PHASE)
            prefs.remove(KEY_ATTEMPT_ROLLBACK_PATH)
            prefs.remove(KEY_ATTEMPT_ROLLBACK_ORIGIN)
            prefs.remove(KEY_BACKUP_SCHEMA_VERSION)
            prefs.remove(KEY_BACKUP_CREATED_AT)
            prefs.remove(KEY_BACKUP_APP_VERSION)
            prefs.remove(KEY_RESTORE_STARTED_AT)
            prefs.remove(KEY_LEGACY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_LEGACY_MUTATION_INTERRUPTED)
            resolved = true
        }
        return resolved
    }

    override suspend fun getAttempt(): RestoreAttempt? {
        val prefs = dataStore.data.first()
        val context = readContext(prefs)
        val id = prefs[KEY_ATTEMPT_ID]
        if (id == null) {
            // Legacy marker has unknown phase and is conservatively Prepared.
            if (prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] != true) return null
            return RestoreAttempt(
                id = LEGACY_ATTEMPT_ID,
                kind = RestoreAttempt.Kind.Restore,
                phase = RestoreAttempt.Phase.Prepared,
                context = context,
                rollbackSnapshotPath = null,
                rollbackOrigin = null,
            )
        }
        // An unparsable phase is unknown state → Prepared (recovery), never a success verdict.
        val phase = prefs[KEY_ATTEMPT_PHASE]
            ?.let { name -> RestoreAttempt.Phase.entries.firstOrNull { it.name == name } }
            ?: RestoreAttempt.Phase.Prepared
        val kind = prefs[KEY_ATTEMPT_KIND]
            ?.let { name -> RestoreAttempt.Kind.entries.firstOrNull { it.name == name } }
            ?: RestoreAttempt.Kind.Restore
        return RestoreAttempt(
            id = id,
            kind = kind,
            phase = phase,
            context = context,
            rollbackSnapshotPath = prefs[KEY_ATTEMPT_ROLLBACK_PATH],
            rollbackOrigin = readRollbackOrigin(prefs, kind),
        )
    }

    /**
     * An absent or unparsable origin reads as recovery — the pre-origin build's terminal, never a
     * false undo success. A [RestoreAttempt.Kind.Restore] drops a stray value.
     */
    private fun readRollbackOrigin(
        prefs: Preferences,
        kind: RestoreAttempt.Kind,
    ): RestoreAttempt.RollbackOrigin? {
        if (kind != RestoreAttempt.Kind.Rollback) return null
        return prefs[KEY_ATTEMPT_ROLLBACK_ORIGIN]
            ?.let { name -> RestoreAttempt.RollbackOrigin.entries.firstOrNull { it.name == name } }
            ?: RestoreAttempt.RollbackOrigin.ScenarioOneRecovery
    }

    private fun readContext(prefs: Preferences): RestoreInProgressContext? = RestoreInProgressContext(
        backupSchemaVersion = prefs[KEY_BACKUP_SCHEMA_VERSION] ?: return null,
        backupCreatedAtEpochMs = prefs[KEY_BACKUP_CREATED_AT] ?: return null,
        backupAppVersion = prefs[KEY_BACKUP_APP_VERSION] ?: return null,
        startedAtEpochMs = prefs[KEY_RESTORE_STARTED_AT] ?: return null,
    )

    override suspend fun markPreRestoreBackupAvailable(originalDataDateEpochMs: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_PRE_RESTORE_AVAILABLE] = true
            prefs[KEY_PRE_RESTORE_ORIGINAL_DATE] = originalDataDateEpochMs
        }
    }

    override suspend fun clearPreRestoreBackupAvailable() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PRE_RESTORE_AVAILABLE)
            prefs.remove(KEY_PRE_RESTORE_ORIGINAL_DATE)
        }
    }

    override fun observePreRestoreBackupAvailable(): Flow<Boolean> =
        dataStore.data
            .map { it[KEY_PRE_RESTORE_AVAILABLE] == true }
            .distinctUntilChanged()

    override suspend fun getPreRestoreOriginalDate(): Long? =
        dataStore.data.first()[KEY_PRE_RESTORE_ORIGINAL_DATE]

    private companion object {
        const val PREFS_NAME = "restore_state_prefs"

        // Attempt-journal wire keys; preserve names or add a migration path.
        val KEY_ATTEMPT_ID = stringPreferencesKey("restore_attempt_id")
        val KEY_ATTEMPT_KIND = stringPreferencesKey("restore_attempt_kind")
        val KEY_ATTEMPT_PHASE = stringPreferencesKey("restore_attempt_phase")
        val KEY_ATTEMPT_ROLLBACK_PATH =
            stringPreferencesKey("restore_attempt_rollback_snapshot_path")
        val KEY_ATTEMPT_ROLLBACK_ORIGIN =
            stringPreferencesKey("restore_attempt_rollback_origin")

        val KEY_BACKUP_SCHEMA_VERSION =
            intPreferencesKey("restore_in_progress_backup_schema_version")
        val KEY_BACKUP_CREATED_AT =
            longPreferencesKey("restore_in_progress_backup_created_at_epoch_ms")
        val KEY_BACKUP_APP_VERSION =
            stringPreferencesKey("restore_in_progress_backup_app_version")
        val KEY_RESTORE_STARTED_AT =
            longPreferencesKey("restore_in_progress_started_at_epoch_ms")

        // Read by getAttempt to synthesize the id-less legacy owner.
        val KEY_LEGACY_RESTORE_IN_PROGRESS = booleanPreferencesKey("restore_in_progress")

        // Write-only ON PURPOSE: since R3 an unresolved attempt is conservatively Prepared, which
        // is already the strongest verdict this flag could produce, so nothing reads it. The
        // removes stay so a pre-R3 install does not carry the orphan key forever.
        val KEY_LEGACY_MUTATION_INTERRUPTED =
            booleanPreferencesKey("restore_mutation_interrupted")

        const val LEGACY_ATTEMPT_ID = "legacy-restore-in-progress"

        val KEY_PRE_RESTORE_AVAILABLE = booleanPreferencesKey("pre_restore_backup_available")
        val KEY_PRE_RESTORE_ORIGINAL_DATE =
            longPreferencesKey("pre_restore_backup_original_date_epoch_ms")
    }
}
