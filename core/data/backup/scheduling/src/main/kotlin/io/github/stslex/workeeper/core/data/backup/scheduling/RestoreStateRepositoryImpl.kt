// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndoTransition
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreOwners
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** DataStore owner of installation-scoped restore protocol state. */
// GUARD: mint the store via DataStoreProviderFactory only. See documentation/tech-debt.md.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RestoreStateRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val recoveryFiles: RestoreRecoveryFiles,
) : RestoreStateRepository {

    @Inject
    constructor(
        storeFactory: DataStoreProviderFactory,
        recoveryFiles: RestoreRecoveryFiles,
    ) : this(
        dataStore = storeFactory.create(PREFS_NAME).dataStore,
        recoveryFiles = recoveryFiles,
    )

    override suspend fun readProtocol(): RestoreProtocolRead =
        readProtocol(recoveryFiles.installEpoch())

    override suspend fun installLegacyState(
        epoch: InstallEpoch,
        attempt: RestoreAttempt?,
        activeUndo: ActiveUndo?,
    ): Boolean {
        val localEpoch = recoveryFiles.installEpoch()
        if (epoch != localEpoch) return false
        validateAttempt(attempt)

        var installed = false
        dataStore.edit { prefs ->
            when (prefs[KEY_INSTALL_EPOCH]) {
                null -> {
                    if (!hasReleasedState(prefs)) {
                        clearAllKnownState(prefs)
                        prefs[KEY_INSTALL_EPOCH] = localEpoch.toString()
                        return@edit
                    }
                    clearAllKnownState(prefs)
                    prefs[KEY_INSTALL_EPOCH] = localEpoch.toString()
                    attempt?.let { writeAttempt(prefs, localEpoch, it) }
                    activeUndo?.let { writeActiveUndo(prefs, localEpoch, it) }
                    installed = true
                }

                localEpoch.toString() -> {
                    reconcileRecordEpochs(prefs, localEpoch)
                    clearReleasedAndObsoleteState(prefs)
                    val current = decodeCurrentOrNull(prefs, localEpoch) ?: return@edit
                    installed = current.attempt == attempt &&
                        current.activeUndo == activeUndo &&
                        current.terminalOutbox == null
                }

                else -> {
                    // A migration snapshot must never overwrite state that raced in from elsewhere.
                    clearAllKnownState(prefs)
                    prefs[KEY_INSTALL_EPOCH] = localEpoch.toString()
                }
            }
        }
        return installed
    }

    override suspend fun beginAttempt(attempt: RestoreAttempt): Boolean {
        require(attempt.phase == RestoreAttempt.Phase.Prepared) {
            "an attempt enters the slot as Prepared, not ${attempt.phase}"
        }
        validateAttempt(attempt)
        val epoch = recoveryFiles.installEpoch()
        var claimed = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val state = decodeCurrentOrNull(prefs, epoch) ?: return@edit
            if (state.terminalOutbox != null) return@edit
            when (state.attempt) {
                attempt -> claimed = true

                null -> {
                    if (!attempt.hasValidUserUndoPointer(state)) return@edit
                    writeAttempt(prefs, epoch, attempt)
                    claimed = true
                }

                else -> claimed = false
            }
        }
        return claimed
    }

    override suspend fun recordAttemptCommitted(attemptId: RestoreOwnerId): Boolean {
        val epoch = recoveryFiles.installEpoch()
        var committed = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val attempt = decodeCurrentOrNull(prefs, epoch)?.attempt ?: return@edit
            if (attempt.id != attemptId) return@edit
            if (attempt.phase == RestoreAttempt.Phase.Committed) {
                committed = true
                return@edit
            }
            prefs[KEY_ATTEMPT_PHASE] = RestoreAttempt.Phase.Committed.name
            committed = true
        }
        return committed
    }

    override suspend fun beginCompensation(
        restoreAttemptId: RestoreOwnerId,
        rollback: RestoreAttempt.Rollback,
    ): Boolean {
        require(rollback.phase == RestoreAttempt.Phase.Prepared) {
            "a compensation enters the slot as Prepared, not ${rollback.phase}"
        }
        val epoch = recoveryFiles.installEpoch()
        var replaced = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val state = decodeCurrentOrNull(prefs, epoch) ?: return@edit
            val restore = state.attempt as? RestoreAttempt.Restore ?: return@edit
            if (restore.id != restoreAttemptId) return@edit
            if (rollback.id == restore.id) return@edit
            if (restore.undoRef != rollback.sourceRef) return@edit
            if (rollback.origin != RestoreAttempt.RollbackOrigin.ScenarioOneRecovery) return@edit
            if (state.terminalOutbox != null) return@edit
            writeAttempt(prefs, epoch, rollback)
            replaced = true
        }
        return replaced
    }

    override suspend fun discardPreparedAttempt(attemptId: RestoreOwnerId): Boolean {
        val epoch = recoveryFiles.installEpoch()
        var discarded = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val attempt = decodeCurrentOrNull(prefs, epoch)?.attempt ?: return@edit
            if (attempt.id != attemptId || attempt.phase != RestoreAttempt.Phase.Prepared) {
                return@edit
            }
            clearAttempt(prefs)
            discarded = true
        }
        return discarded
    }

    override suspend fun finalizeAttempt(
        attemptId: RestoreOwnerId,
        activeUndoTransition: ActiveUndoTransition,
        terminal: RestoreTerminal,
    ): Boolean {
        if (terminal.owner != attemptId) return false
        val epoch = recoveryFiles.installEpoch()
        var finalized = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val state = decodeCurrentOrNull(prefs, epoch) ?: return@edit
            if (state.attempt == null && state.terminalOutbox == terminal) {
                finalized = finalizedStateMatches(state, activeUndoTransition, terminal)
                return@edit
            }
            val attempt = state.attempt ?: return@edit
            if (attempt.id != attemptId || attempt.phase != RestoreAttempt.Phase.Committed) {
                return@edit
            }
            if (state.terminalOutbox != null ||
                !finalizationMatches(attempt, activeUndoTransition, terminal) ||
                !attempt.hasValidUserUndoPointer(state)
            ) {
                return@edit
            }

            applyActiveUndoTransition(
                transition = activeUndoTransition,
                currentActiveUndo = state.activeUndo,
                clear = { clearActiveUndo(prefs) },
                write = { writeActiveUndo(prefs, epoch, it) },
            )
            clearAttempt(prefs)
            writeTerminal(prefs, epoch, terminal)
            finalized = true
        }
        return finalized
    }

    override suspend fun acknowledgeTerminal(owner: RestoreOwnerId): Boolean {
        val epoch = recoveryFiles.installEpoch()
        var acknowledged = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val terminal = decodeCurrentOrNull(prefs, epoch)?.terminalOutbox ?: return@edit
            if (terminal.owner != owner) return@edit
            clearTerminal(prefs)
            acknowledged = true
        }
        return acknowledged
    }

    override suspend fun abandonInterruptedAttempt(attemptId: RestoreOwnerId): Boolean {
        val epoch = recoveryFiles.installEpoch()
        var abandoned = false
        dataStore.edit { prefs ->
            if (!reconcileForMutation(prefs, epoch)) return@edit
            val attempt = decodeCurrentOrNull(prefs, epoch)?.attempt
            if (!attempt.isAbandonableInterrupted(attemptId)) return@edit
            clearAttempt(prefs)
            clearActiveUndo(prefs)
            clearTerminal(prefs)
            abandoned = true
        }
        return abandoned
    }

    override fun observeActiveUndo(): Flow<ActiveUndo?> = flow {
        val localEpoch = recoveryFiles.installEpoch()
        readProtocol(localEpoch)
        emitAll(
            dataStore.data.map { prefs ->
                if (prefs[KEY_INSTALL_EPOCH] != localEpoch.toString()) return@map null
                val recordEpoch = prefs[KEY_ACTIVE_UNDO_EPOCH]
                if (recordEpoch != null && recordEpoch != localEpoch.toString()) return@map null
                runCatching { decodeActiveUndo(prefs, localEpoch) }.getOrNull()
            },
        )
    }.distinctUntilChanged()

    private suspend fun readProtocol(localEpoch: InstallEpoch): RestoreProtocolRead {
        lateinit var result: RestoreProtocolRead
        dataStore.edit { prefs ->
            val storedEpoch = prefs[KEY_INSTALL_EPOCH]
            if (storedEpoch == null && hasReleasedState(prefs)) {
                result = RestoreProtocolRead.Legacy(
                    epoch = localEpoch,
                    state = readLegacyState(prefs),
                )
                return@edit
            }
            if (storedEpoch != localEpoch.toString()) {
                clearAllKnownState(prefs)
                prefs[KEY_INSTALL_EPOCH] = localEpoch.toString()
            } else {
                reconcileRecordEpochs(prefs, localEpoch)
                clearReleasedAndObsoleteState(prefs)
            }
            result = decodeCurrent(prefs, localEpoch)
        }
        return result
    }

    /** Returns false only while released state still awaits the explicit migration table. */
    private fun reconcileForMutation(
        prefs: MutablePreferences,
        localEpoch: InstallEpoch,
    ): Boolean {
        val storedEpoch = prefs[KEY_INSTALL_EPOCH]
        if (storedEpoch == null && hasReleasedState(prefs)) return false
        if (storedEpoch != localEpoch.toString()) {
            clearAllKnownState(prefs)
            prefs[KEY_INSTALL_EPOCH] = localEpoch.toString()
            return true
        }
        reconcileRecordEpochs(prefs, localEpoch)
        clearReleasedAndObsoleteState(prefs)
        return true
    }

    private fun reconcileRecordEpochs(
        prefs: MutablePreferences,
        localEpoch: InstallEpoch,
    ) {
        val epoch = localEpoch.toString()
        val foreignRecord = listOf(
            prefs[KEY_ATTEMPT_EPOCH],
            prefs[KEY_ACTIVE_UNDO_EPOCH],
            prefs[KEY_TERMINAL_EPOCH],
        ).any { recordEpoch -> recordEpoch != null && recordEpoch != epoch }
        if (foreignRecord) {
            // GUARD: a mixed-epoch envelope is foreign as a whole. See backup-recovery.md.
            clearAllKnownState(prefs)
            prefs[KEY_INSTALL_EPOCH] = epoch
        }
    }

    private fun decodeCurrent(
        prefs: Preferences,
        epoch: InstallEpoch,
    ): RestoreProtocolRead = try {
        RestoreProtocolRead.Current(
            RestoreProtocolState(
                installEpoch = epoch,
                attempt = decodeAttempt(prefs, epoch),
                activeUndo = decodeActiveUndo(prefs, epoch),
                terminalOutbox = decodeTerminal(prefs, epoch),
            ),
        )
    } catch (failure: ProtocolDecodeException) {
        RestoreProtocolRead.Corrupt(epoch, failure.message.orEmpty())
    } catch (failure: IllegalArgumentException) {
        RestoreProtocolRead.Corrupt(epoch, failure.message.orEmpty())
    }

    private fun decodeCurrentOrNull(
        prefs: Preferences,
        epoch: InstallEpoch,
    ): RestoreProtocolState? =
        (decodeCurrent(prefs, epoch) as? RestoreProtocolRead.Current)?.state

    private fun decodeAttempt(
        prefs: Preferences,
        epoch: InstallEpoch,
    ): RestoreAttempt? {
        val rawEpoch = prefs[KEY_ATTEMPT_EPOCH]
        val rawId = prefs[KEY_ATTEMPT_ID]
        val rawType = prefs[KEY_ATTEMPT_TYPE]
        val rawPhase = prefs[KEY_ATTEMPT_PHASE]
        val hasDetails = listOf(
            prefs[KEY_RESTORE_UNDO_REF],
            prefs[KEY_RESTORE_SOURCE_REF],
            prefs[KEY_ROLLBACK_SOURCE_REF],
            prefs[KEY_ROLLBACK_ORIGIN],
            prefs[KEY_ATTEMPT_BACKUP_SCHEMA],
            prefs[KEY_ATTEMPT_BACKUP_CREATED_AT],
            prefs[KEY_ATTEMPT_BACKUP_APP_VERSION],
            prefs[KEY_ATTEMPT_STARTED_AT],
        ).any { it != null }
        val hasPayload = rawId != null || rawType != null || rawPhase != null || hasDetails
        if (!hasPayload && rawEpoch == null) return null
        requireRecordEpoch("attempt", rawEpoch, epoch)
        val id = restoreOwner(rawId ?: corrupt("attempt id is missing"), "attempt id")
        val phase = enumValue<RestoreAttempt.Phase>(
            rawPhase ?: corrupt("attempt phase is missing"),
            "attempt phase",
        )
        return when (rawType ?: corrupt("attempt type is missing")) {
            TYPE_RESTORE -> RestoreAttempt.Restore(
                id = id,
                phase = phase,
                context = decodeContext(prefs),
                undoRef = prefs[KEY_RESTORE_UNDO_REF]?.let { UndoRef(restoreOwner(it, "undo ref")) },
                sourceRef = prefs[KEY_RESTORE_SOURCE_REF]
                    ?.let { RestoreSourceRef(restoreOwner(it, "restore source ref")) },
            )

            TYPE_ROLLBACK -> RestoreAttempt.Rollback(
                id = id,
                phase = phase,
                sourceRef = UndoRef(
                    restoreOwner(
                        prefs[KEY_ROLLBACK_SOURCE_REF]
                            ?: corrupt("rollback source ref is missing"),
                        "rollback source ref",
                    ),
                ),
                origin = enumValue(
                    prefs[KEY_ROLLBACK_ORIGIN] ?: corrupt("rollback origin is missing"),
                    "rollback origin",
                ),
            )

            else -> corrupt("attempt type is unknown")
        }.also(::validateAttempt)
    }

    private fun decodeActiveUndo(
        prefs: Preferences,
        epoch: InstallEpoch,
    ): ActiveUndo? {
        val rawEpoch = prefs[KEY_ACTIVE_UNDO_EPOCH]
        val rawRef = prefs[KEY_ACTIVE_UNDO_REF]
        val rawDate = prefs[KEY_ACTIVE_UNDO_DATE]
        if (rawEpoch == null && rawRef == null && rawDate == null) return null
        requireRecordEpoch("active undo", rawEpoch, epoch)
        return ActiveUndo(
            ref = UndoRef(
                restoreOwner(
                    rawRef ?: corrupt("active undo ref is missing"),
                    "active undo ref",
                ),
            ),
            originalDataDateEpochMs = rawDate ?: corrupt("active undo date is missing"),
        )
    }

    private fun decodeTerminal(
        prefs: Preferences,
        epoch: InstallEpoch,
    ): RestoreTerminal? {
        val rawEpoch = prefs[KEY_TERMINAL_EPOCH]
        val rawOwner = prefs[KEY_TERMINAL_OWNER]
        val rawType = prefs[KEY_TERMINAL_TYPE]
        val hasPayload = rawOwner != null || rawType != null ||
            prefs[KEY_TERMINAL_RESTORED_AT] != null ||
            prefs[KEY_TERMINAL_PREVIOUS_AVAILABLE] != null ||
            prefs[KEY_TERMINAL_FAILURE_REASON] != null
        if (!hasPayload && rawEpoch == null) return null
        requireRecordEpoch("terminal outbox", rawEpoch, epoch)
        val owner = restoreOwner(rawOwner ?: corrupt("terminal owner is missing"), "terminal owner")
        return when (rawType ?: corrupt("terminal type is missing")) {
            TERMINAL_RESTORE_SUCCEEDED -> RestoreTerminal.RestoreSucceeded(
                owner = owner,
                restoredAtEpochMs = prefs[KEY_TERMINAL_RESTORED_AT]
                    ?: corrupt("restore-success timestamp is missing"),
                previousVersionAvailable = prefs[KEY_TERMINAL_PREVIOUS_AVAILABLE]
                    ?: corrupt("restore-success availability is missing"),
            )

            TERMINAL_RESTORE_FAILED -> RestoreTerminal.RestoreFailed(
                owner = owner,
                reason = enumValue(
                    prefs[KEY_TERMINAL_FAILURE_REASON]
                        ?: corrupt("restore-failure reason is missing"),
                    "restore-failure reason",
                ),
            )

            TERMINAL_UNDO_SUCCEEDED -> RestoreTerminal.UndoSucceeded(owner)
            else -> corrupt("terminal type is unknown")
        }
    }

    private fun decodeContext(prefs: Preferences): RestoreInProgressContext? {
        val schema = prefs[KEY_ATTEMPT_BACKUP_SCHEMA]
        val createdAt = prefs[KEY_ATTEMPT_BACKUP_CREATED_AT]
        val appVersion = prefs[KEY_ATTEMPT_BACKUP_APP_VERSION]
        val startedAt = prefs[KEY_ATTEMPT_STARTED_AT]
        if (listOf(schema, createdAt, appVersion, startedAt).all { it == null }) {
            return null
        }
        return RestoreInProgressContext(
            backupSchemaVersion = schema ?: corrupt("restore context schema is missing"),
            backupCreatedAtEpochMs = createdAt ?: corrupt("restore context created-at is missing"),
            backupAppVersion = appVersion ?: corrupt("restore context app version is missing"),
            startedAtEpochMs = startedAt ?: corrupt("restore context started-at is missing"),
        )
    }

    private fun writeAttempt(
        prefs: MutablePreferences,
        epoch: InstallEpoch,
        attempt: RestoreAttempt,
    ) {
        clearAttempt(prefs)
        prefs[KEY_ATTEMPT_EPOCH] = epoch.toString()
        prefs[KEY_ATTEMPT_ID] = attempt.id.toString()
        prefs[KEY_ATTEMPT_PHASE] = attempt.phase.name
        when (attempt) {
            is RestoreAttempt.Restore -> {
                prefs[KEY_ATTEMPT_TYPE] = TYPE_RESTORE
                attempt.context?.let { context ->
                    prefs[KEY_ATTEMPT_BACKUP_SCHEMA] = context.backupSchemaVersion
                    prefs[KEY_ATTEMPT_BACKUP_CREATED_AT] = context.backupCreatedAtEpochMs
                    prefs[KEY_ATTEMPT_BACKUP_APP_VERSION] = context.backupAppVersion
                    prefs[KEY_ATTEMPT_STARTED_AT] = context.startedAtEpochMs
                }
                attempt.undoRef?.let { prefs[KEY_RESTORE_UNDO_REF] = it.toString() }
                attempt.sourceRef?.let { prefs[KEY_RESTORE_SOURCE_REF] = it.toString() }
            }

            is RestoreAttempt.Rollback -> {
                prefs[KEY_ATTEMPT_TYPE] = TYPE_ROLLBACK
                prefs[KEY_ROLLBACK_SOURCE_REF] = attempt.sourceRef.toString()
                prefs[KEY_ROLLBACK_ORIGIN] = attempt.origin.name
            }
        }
    }

    private fun writeActiveUndo(
        prefs: MutablePreferences,
        epoch: InstallEpoch,
        activeUndo: ActiveUndo,
    ) {
        clearActiveUndo(prefs)
        prefs[KEY_ACTIVE_UNDO_EPOCH] = epoch.toString()
        prefs[KEY_ACTIVE_UNDO_REF] = activeUndo.ref.toString()
        prefs[KEY_ACTIVE_UNDO_DATE] = activeUndo.originalDataDateEpochMs
    }

    private fun writeTerminal(
        prefs: MutablePreferences,
        epoch: InstallEpoch,
        terminal: RestoreTerminal,
    ) {
        clearTerminal(prefs)
        prefs[KEY_TERMINAL_EPOCH] = epoch.toString()
        prefs[KEY_TERMINAL_OWNER] = terminal.owner.toString()
        when (terminal) {
            is RestoreTerminal.RestoreSucceeded -> {
                prefs[KEY_TERMINAL_TYPE] = TERMINAL_RESTORE_SUCCEEDED
                prefs[KEY_TERMINAL_RESTORED_AT] = terminal.restoredAtEpochMs
                prefs[KEY_TERMINAL_PREVIOUS_AVAILABLE] = terminal.previousVersionAvailable
            }

            is RestoreTerminal.RestoreFailed -> {
                prefs[KEY_TERMINAL_TYPE] = TERMINAL_RESTORE_FAILED
                prefs[KEY_TERMINAL_FAILURE_REASON] = terminal.reason.name
            }

            is RestoreTerminal.UndoSucceeded -> {
                prefs[KEY_TERMINAL_TYPE] = TERMINAL_UNDO_SUCCEEDED
            }
        }
    }

    private fun finalizationMatches(
        attempt: RestoreAttempt,
        transition: ActiveUndoTransition,
        terminal: RestoreTerminal,
    ): Boolean = when (attempt) {
        is RestoreAttempt.Restore -> {
            val replacement = (transition as? ActiveUndoTransition.Replace)?.activeUndo
            transition is ActiveUndoTransition.Replace &&
                terminal is RestoreTerminal.RestoreSucceeded &&
                terminal.previousVersionAvailable == (replacement != null) &&
                (replacement == null || replacement.ref == attempt.undoRef)
        }

        is RestoreAttempt.Rollback -> {
            val terminalMatchesOrigin = when (attempt.origin) {
                RestoreAttempt.RollbackOrigin.UserUndo -> terminal is RestoreTerminal.UndoSucceeded
                RestoreAttempt.RollbackOrigin.ScenarioOneRecovery ->
                    terminal is RestoreTerminal.RestoreFailed
            }
            transition is ActiveUndoTransition.ClearIf &&
                transition.appliedRef == attempt.sourceRef &&
                terminalMatchesOrigin
        }
    }

    private fun finalizedStateMatches(
        state: RestoreProtocolState,
        transition: ActiveUndoTransition,
        terminal: RestoreTerminal,
    ): Boolean = when (transition) {
        is ActiveUndoTransition.Replace ->
            state.activeUndo == transition.activeUndo &&
                terminal is RestoreTerminal.RestoreSucceeded &&
                terminal.previousVersionAvailable == (transition.activeUndo != null)

        // GUARD: the terminal outbox does not retain the applied ref; never approve a guessed ref.
        is ActiveUndoTransition.ClearIf -> false
    }

    private fun validateAttempt(attempt: RestoreAttempt?) {
        if (attempt !is RestoreAttempt.Restore) return
        val undoRef = attempt.undoRef
        val sourceRef = attempt.sourceRef
        require(undoRef == null || undoRef.owner == attempt.id) {
            "a restore undo ref must be owned by its attempt"
        }
        require(sourceRef == null || sourceRef.owner == attempt.id) {
            "a staged restore source must be owned by its attempt"
        }
        if (attempt.id != LegacyRestoreOwners.InterruptedAttempt) {
            require(attempt.context != null && undoRef != null && sourceRef != null) {
                "only the released interrupted-restore owner may omit protocol assets"
            }
        }
    }

    private fun readLegacyState(prefs: Preferences): LegacyRestoreState = LegacyRestoreState(
        restoreInProgress = prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] == true,
        context = readLegacyContext(prefs),
        preRestoreBackupAvailable = prefs[KEY_LEGACY_PRE_RESTORE_AVAILABLE] == true,
        preRestoreOriginalDateEpochMs = prefs[KEY_LEGACY_PRE_RESTORE_DATE],
    )

    private fun readLegacyContext(prefs: Preferences): RestoreInProgressContext? =
        RestoreInProgressContext(
            backupSchemaVersion = prefs[KEY_LEGACY_BACKUP_SCHEMA] ?: return null,
            backupCreatedAtEpochMs = prefs[KEY_LEGACY_BACKUP_CREATED_AT] ?: return null,
            backupAppVersion = prefs[KEY_LEGACY_BACKUP_APP_VERSION] ?: return null,
            startedAtEpochMs = prefs[KEY_LEGACY_RESTORE_STARTED_AT] ?: return null,
        )

    private fun hasReleasedState(prefs: Preferences): Boolean =
        prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] != null ||
            prefs[KEY_LEGACY_BACKUP_SCHEMA] != null ||
            prefs[KEY_LEGACY_BACKUP_CREATED_AT] != null ||
            prefs[KEY_LEGACY_BACKUP_APP_VERSION] != null ||
            prefs[KEY_LEGACY_RESTORE_STARTED_AT] != null ||
            prefs[KEY_LEGACY_PRE_RESTORE_AVAILABLE] != null ||
            prefs[KEY_LEGACY_PRE_RESTORE_DATE] != null

    private fun requireRecordEpoch(
        record: String,
        rawEpoch: String?,
        epoch: InstallEpoch,
    ) {
        if (rawEpoch == null) corrupt("$record epoch is missing")
        if (rawEpoch != epoch.toString()) corrupt("$record epoch is foreign")
    }

    private companion object {
        const val PREFS_NAME = "restore_state_prefs"

        val KEY_INSTALL_EPOCH = stringPreferencesKey("restore_protocol_install_epoch")

        val KEY_ATTEMPT_EPOCH = stringPreferencesKey("restore_protocol_attempt_epoch")
        val KEY_ATTEMPT_ID = stringPreferencesKey("restore_protocol_attempt_id")
        val KEY_ATTEMPT_TYPE = stringPreferencesKey("restore_protocol_attempt_type")
        val KEY_ATTEMPT_PHASE = stringPreferencesKey("restore_protocol_attempt_phase")
        val KEY_RESTORE_UNDO_REF = stringPreferencesKey("restore_protocol_restore_undo_ref")
        val KEY_RESTORE_SOURCE_REF = stringPreferencesKey("restore_protocol_restore_source_ref")
        val KEY_ROLLBACK_SOURCE_REF = stringPreferencesKey("restore_protocol_rollback_source_ref")
        val KEY_ROLLBACK_ORIGIN = stringPreferencesKey("restore_protocol_rollback_origin")
        val KEY_ATTEMPT_BACKUP_SCHEMA =
            intPreferencesKey("restore_protocol_attempt_backup_schema_version")
        val KEY_ATTEMPT_BACKUP_CREATED_AT =
            longPreferencesKey("restore_protocol_attempt_backup_created_at_epoch_ms")
        val KEY_ATTEMPT_BACKUP_APP_VERSION =
            stringPreferencesKey("restore_protocol_attempt_backup_app_version")
        val KEY_ATTEMPT_STARTED_AT =
            longPreferencesKey("restore_protocol_attempt_started_at_epoch_ms")

        val KEY_ACTIVE_UNDO_EPOCH = stringPreferencesKey("restore_protocol_active_undo_epoch")
        val KEY_ACTIVE_UNDO_REF = stringPreferencesKey("restore_protocol_active_undo_ref")
        val KEY_ACTIVE_UNDO_DATE =
            longPreferencesKey("restore_protocol_active_undo_original_date_epoch_ms")

        val KEY_TERMINAL_EPOCH = stringPreferencesKey("restore_protocol_terminal_outbox_epoch")
        val KEY_TERMINAL_OWNER = stringPreferencesKey("restore_protocol_terminal_outbox_owner")
        val KEY_TERMINAL_TYPE = stringPreferencesKey("restore_protocol_terminal_outbox_type")
        val KEY_TERMINAL_RESTORED_AT =
            longPreferencesKey("restore_protocol_terminal_restored_at_epoch_ms")
        val KEY_TERMINAL_PREVIOUS_AVAILABLE =
            booleanPreferencesKey("restore_protocol_terminal_previous_version_available")
        val KEY_TERMINAL_FAILURE_REASON =
            stringPreferencesKey("restore_protocol_terminal_failure_reason")

        const val TYPE_RESTORE = "Restore"
        const val TYPE_ROLLBACK = "Rollback"
        const val TERMINAL_RESTORE_SUCCEEDED = "RestoreSucceeded"
        const val TERMINAL_RESTORE_FAILED = "RestoreFailed"
        const val TERMINAL_UNDO_SUCCEEDED = "UndoSucceeded"

        // Released wire keys. They stay readable until the explicit rollout table is installed.
        val KEY_LEGACY_RESTORE_IN_PROGRESS = booleanPreferencesKey("restore_in_progress")
        val KEY_LEGACY_BACKUP_SCHEMA =
            intPreferencesKey("restore_in_progress_backup_schema_version")
        val KEY_LEGACY_BACKUP_CREATED_AT =
            longPreferencesKey("restore_in_progress_backup_created_at_epoch_ms")
        val KEY_LEGACY_BACKUP_APP_VERSION =
            stringPreferencesKey("restore_in_progress_backup_app_version")
        val KEY_LEGACY_RESTORE_STARTED_AT =
            longPreferencesKey("restore_in_progress_started_at_epoch_ms")
        val KEY_LEGACY_PRE_RESTORE_AVAILABLE =
            booleanPreferencesKey("pre_restore_backup_available")
        val KEY_LEGACY_PRE_RESTORE_DATE =
            longPreferencesKey("pre_restore_backup_original_date_epoch_ms")

        // GUARD: remove obsolete path-bearing keys without interpreting their values.
        val KEY_OBSOLETE_ATTEMPT_ID = stringPreferencesKey("restore_attempt_id")
        val KEY_OBSOLETE_ATTEMPT_KIND = stringPreferencesKey("restore_attempt_kind")
        val KEY_OBSOLETE_ATTEMPT_PHASE = stringPreferencesKey("restore_attempt_phase")
        val KEY_OBSOLETE_ROLLBACK_PATH =
            stringPreferencesKey("restore_attempt_rollback_snapshot_path")
        val KEY_OBSOLETE_ROLLBACK_ORIGIN =
            stringPreferencesKey("restore_attempt_rollback_origin")
        val KEY_OBSOLETE_MUTATION_INTERRUPTED =
            booleanPreferencesKey("restore_mutation_interrupted")

        fun clearAttempt(prefs: MutablePreferences) {
            prefs.remove(KEY_ATTEMPT_EPOCH)
            prefs.remove(KEY_ATTEMPT_ID)
            prefs.remove(KEY_ATTEMPT_TYPE)
            prefs.remove(KEY_ATTEMPT_PHASE)
            prefs.remove(KEY_RESTORE_UNDO_REF)
            prefs.remove(KEY_RESTORE_SOURCE_REF)
            prefs.remove(KEY_ROLLBACK_SOURCE_REF)
            prefs.remove(KEY_ROLLBACK_ORIGIN)
            prefs.remove(KEY_ATTEMPT_BACKUP_SCHEMA)
            prefs.remove(KEY_ATTEMPT_BACKUP_CREATED_AT)
            prefs.remove(KEY_ATTEMPT_BACKUP_APP_VERSION)
            prefs.remove(KEY_ATTEMPT_STARTED_AT)
        }

        fun clearActiveUndo(prefs: MutablePreferences) {
            prefs.remove(KEY_ACTIVE_UNDO_EPOCH)
            prefs.remove(KEY_ACTIVE_UNDO_REF)
            prefs.remove(KEY_ACTIVE_UNDO_DATE)
        }

        fun clearTerminal(prefs: MutablePreferences) {
            prefs.remove(KEY_TERMINAL_EPOCH)
            prefs.remove(KEY_TERMINAL_OWNER)
            prefs.remove(KEY_TERMINAL_TYPE)
            prefs.remove(KEY_TERMINAL_RESTORED_AT)
            prefs.remove(KEY_TERMINAL_PREVIOUS_AVAILABLE)
            prefs.remove(KEY_TERMINAL_FAILURE_REASON)
        }

        fun clearReleasedAndObsoleteState(prefs: MutablePreferences) {
            prefs.remove(KEY_LEGACY_RESTORE_IN_PROGRESS)
            prefs.remove(KEY_LEGACY_BACKUP_SCHEMA)
            prefs.remove(KEY_LEGACY_BACKUP_CREATED_AT)
            prefs.remove(KEY_LEGACY_BACKUP_APP_VERSION)
            prefs.remove(KEY_LEGACY_RESTORE_STARTED_AT)
            prefs.remove(KEY_LEGACY_PRE_RESTORE_AVAILABLE)
            prefs.remove(KEY_LEGACY_PRE_RESTORE_DATE)
            prefs.remove(KEY_OBSOLETE_ATTEMPT_ID)
            prefs.remove(KEY_OBSOLETE_ATTEMPT_KIND)
            prefs.remove(KEY_OBSOLETE_ATTEMPT_PHASE)
            prefs.remove(KEY_OBSOLETE_ROLLBACK_PATH)
            prefs.remove(KEY_OBSOLETE_ROLLBACK_ORIGIN)
            prefs.remove(KEY_OBSOLETE_MUTATION_INTERRUPTED)
        }

        fun clearAllKnownState(prefs: MutablePreferences) {
            prefs.remove(KEY_INSTALL_EPOCH)
            clearAttempt(prefs)
            clearActiveUndo(prefs)
            clearTerminal(prefs)
            clearReleasedAndObsoleteState(prefs)
        }
    }
}

internal fun RestoreAttempt.hasValidUserUndoPointer(state: RestoreProtocolState): Boolean =
    this !is RestoreAttempt.Rollback ||
        origin != RestoreAttempt.RollbackOrigin.UserUndo ||
        state.activeUndo?.ref == sourceRef

internal fun applyActiveUndoTransition(
    transition: ActiveUndoTransition,
    currentActiveUndo: ActiveUndo?,
    clear: () -> Unit,
    write: (ActiveUndo) -> Unit,
) {
    when (transition) {
        is ActiveUndoTransition.Replace -> {
            clear()
            transition.activeUndo?.let(write)
        }

        is ActiveUndoTransition.ClearIf -> {
            if (currentActiveUndo?.ref == transition.appliedRef) clear()
        }
    }
}

internal fun corrupt(reason: String): Nothing = throw ProtocolDecodeException(reason)

internal fun restoreOwner(raw: String, label: String): RestoreOwnerId =
    try {
        RestoreOwnerId(raw)
    } catch (_: IllegalArgumentException) {
        corrupt("$label is invalid")
    }

internal fun RestoreAttempt?.isAbandonableInterrupted(attemptId: RestoreOwnerId): Boolean {
    if (this !is RestoreAttempt.Restore) return false
    if (id != attemptId) return false
    if (id != LegacyRestoreOwners.InterruptedAttempt) return false
    if (phase != RestoreAttempt.Phase.Prepared) return false
    if (undoRef != null) return false
    return sourceRef == null
}

private inline fun <reified T : Enum<T>> enumValue(raw: String, label: String): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: corrupt("$label is unknown")

private class ProtocolDecodeException(message: String) : IllegalStateException(message)
