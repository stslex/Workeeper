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
 * DataStore-backed implementation of [RestoreStateRepository]. Lives in the
 * scheduling module alongside [BackupPreferencesRepositoryImpl] because both
 * own DataStore Preferences files in the same module-local layout.
 *
 * Storage: a separate Preferences file (`restore_state_prefs.preferences_pb`)
 * so the restore-recovery flags stay isolated from the auto-backup schedule
 * / last-error tuple owned by `BackupPreferencesRepository`. The two sets of
 * keys have orthogonal lifecycles and clearing one must never disturb the
 * other.
 *
 * Key names are **wire format** — same stability rule as
 * `feature/app-dialogs/impl/.../AppDialogKeys`. Never rename without the
 * deprecation path (add new key, write both, ship, remove old). Renaming a
 * `restore_in_progress` key mid-flow would lose a user's pending dialog on
 * app update.
 */
// App-Scope Collapse Step 3 (SB1): Hilt @Inject/@Singleton stripped, @Binds removed; Metro-owned via
// @ContributesBinding(AppScope). Public for cross-module aggregation (D1; never hand-construct — resolve
// via DI).
//
// Mint the store through DataStoreProviderFactory only: a second AppGraph that built its own would
// throw "There are multiple DataStores active" on the second read. Pinned by app/app androidTest
// AppScopeDataStoreSingletonTest. Bind the internal primary ctor in unit tests, not the provider.
// Mechanism and evidence: documentation/tech-debt.md -> "DataStore singleton bypass".
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
        var claimed = false
        dataStore.edit { prefs ->
            val ownerId = prefs[KEY_ATTEMPT_ID]
            val legacyMarker = prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] == true
            // Owner isolation (R4 invariant 5). A DIFFERENT unresolved attempt owns the slot:
            // refuse rather than inherit its bookkeeping. Re-claiming with the same id is
            // idempotent — a retried submission of one attempt is not a second. A pre-R4 legacy
            // marker (id-less `restore_in_progress`) is owned by exactly ONE synthetic id —
            // [LEGACY_ATTEMPT_ID], the id `getAttempt` hands the recovery path — and its claim
            // CONVERTS the marker into an owner-scoped record in this same atomic edit. Any
            // other id is refused, exactly as against a live owned record.
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
            // The atomic legacy → owner-scoped conversion: once the synthetic owner has an
            // id-keyed record, the id-less boolean would only shadow it.
            prefs.remove(KEY_LEGACY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_LEGACY_MUTATION_INTERRUPTED)
            attempt.rollbackSnapshotPath
                ?.let { prefs[KEY_ATTEMPT_ROLLBACK_PATH] = it }
                ?: prefs.remove(KEY_ATTEMPT_ROLLBACK_PATH)
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
            // Owner isolation (R4 invariant 5): an id-less legacy marker is owned by exactly
            // the synthetic [LEGACY_ATTEMPT_ID] — an ARBITRARY attempt id must not clear it.
            // (Pre-R4, any refused attempt's rejection compensation could erase the legacy
            // journal here, forgetting an interrupted restore entirely.) The migration state
            // cannot outlive every attempt: the recovery path claims it under the synthetic id,
            // which converts it to an owned record that resolves normally.
            val ownsLegacy = ownerId == null &&
                prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] == true &&
                attemptId == LEGACY_ATTEMPT_ID
            if (ownerId != attemptId && !ownsLegacy) return@edit
            prefs.remove(KEY_ATTEMPT_ID)
            prefs.remove(KEY_ATTEMPT_KIND)
            prefs.remove(KEY_ATTEMPT_PHASE)
            prefs.remove(KEY_ATTEMPT_ROLLBACK_PATH)
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
            // Legacy migration (pre-R3 installs): a `restore_in_progress` marker written by an
            // older build carries no phase, so its outcome is UNKNOWN — the conservative
            // reading is Prepared, which routes the next launch through recovery instead of
            // letting a schema peek claim a success the old flags cannot prove.
            if (prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] != true) return null
            return RestoreAttempt(
                id = LEGACY_ATTEMPT_ID,
                kind = RestoreAttempt.Kind.Restore,
                phase = RestoreAttempt.Phase.Prepared,
                context = context,
                rollbackSnapshotPath = null,
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
        )
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

        // Attempt-journal keys (R3). Wire format — never rename without a deprecation path.
        val KEY_ATTEMPT_ID = stringPreferencesKey("restore_attempt_id")
        val KEY_ATTEMPT_KIND = stringPreferencesKey("restore_attempt_kind")
        val KEY_ATTEMPT_PHASE = stringPreferencesKey("restore_attempt_phase")
        val KEY_ATTEMPT_ROLLBACK_PATH =
            stringPreferencesKey("restore_attempt_rollback_snapshot_path")

        val KEY_BACKUP_SCHEMA_VERSION =
            intPreferencesKey("restore_in_progress_backup_schema_version")
        val KEY_BACKUP_CREATED_AT =
            longPreferencesKey("restore_in_progress_backup_created_at_epoch_ms")
        val KEY_BACKUP_APP_VERSION =
            stringPreferencesKey("restore_in_progress_backup_app_version")
        val KEY_RESTORE_STARTED_AT =
            longPreferencesKey("restore_in_progress_started_at_epoch_ms")

        // Read-only legacy flags from pre-R3 installs; cleared when an attempt resolves.
        val KEY_LEGACY_RESTORE_IN_PROGRESS = booleanPreferencesKey("restore_in_progress")
        val KEY_LEGACY_MUTATION_INTERRUPTED =
            booleanPreferencesKey("restore_mutation_interrupted")

        const val LEGACY_ATTEMPT_ID = "legacy-restore-in-progress"

        val KEY_PRE_RESTORE_AVAILABLE = booleanPreferencesKey("pre_restore_backup_available")
        val KEY_PRE_RESTORE_ORIGINAL_DATE =
            longPreferencesKey("pre_restore_backup_original_date_epoch_ms")
    }
}
