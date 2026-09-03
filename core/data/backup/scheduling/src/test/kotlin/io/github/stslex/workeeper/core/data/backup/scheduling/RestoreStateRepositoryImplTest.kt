// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.scheduling

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndo
import io.github.stslex.workeeper.core.data.backup.api.restore.ActiveUndoTransition
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.LegacyRestoreOwners
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreInProgressContext
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolRead
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** Persisted Preferences DataStore coverage for the installation-scoped restore transaction. */
internal class RestoreStateRepositoryImplTest {

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var tempFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var files: FakeRestoreRecoveryFiles
    private lateinit var repo: RestoreStateRepositoryImpl

    @BeforeEach
    fun setUp() {
        tempFile = File.createTempFile(PREFS_FILE_NAME, ".preferences_pb").also { it.delete() }
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { tempFile }
        files = FakeRestoreRecoveryFiles(EPOCH_A)
        repo = RestoreStateRepositoryImpl(dataStore, files)
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `empty store installs the local epoch and returns empty current state`() = runTest {
        val state = currentState()

        assertEquals(EPOCH_A, state.installEpoch)
        assertNull(state.attempt)
        assertNull(state.activeUndo)
        assertNull(state.terminalOutbox)
        assertEquals(EPOCH_A.toString(), dataStore.data.first()[KEY_INSTALL_EPOCH])
    }

    @Test
    fun `installation epoch remains stable across repository reconstruction`() = runTest {
        assertEquals(EPOCH_A, currentState().installEpoch)

        val reconstructed = RestoreStateRepositoryImpl(
            dataStore = dataStore,
            recoveryFiles = FakeRestoreRecoveryFiles(EPOCH_A),
        )
        val read = reconstructed.readProtocol()

        assertEquals(EPOCH_A, (read as RestoreProtocolRead.Current).state.installEpoch)
        assertEquals(EPOCH_A.toString(), dataStore.data.first()[KEY_INSTALL_EPOCH])
    }

    @Test
    fun `released marker and availability return one legacy snapshot before epoch installation`() =
        runTest {
            writeReleasedState(
                restoreInProgress = true,
                context = CONTEXT,
                available = true,
                originalDate = ORIGINAL_DATE,
            )

            val read = repo.readProtocol()

            assertTrue(read is RestoreProtocolRead.Legacy)
            val legacy = (read as RestoreProtocolRead.Legacy)
            assertEquals(EPOCH_A, legacy.epoch)
            assertTrue(legacy.state.restoreInProgress)
            assertEquals(CONTEXT, legacy.state.context)
            assertTrue(legacy.state.preRestoreBackupAvailable)
            assertEquals(ORIGINAL_DATE, legacy.state.preRestoreOriginalDateEpochMs)
            assertNull(dataStore.data.first()[KEY_INSTALL_EPOCH])
        }

    @Test
    fun `released keys take precedence over tokenless unreleased journal keys`() = runTest {
        writeReleasedState(
            restoreInProgress = true,
            context = CONTEXT,
            available = false,
            originalDate = null,
        )
        dataStore.edit { prefs ->
            prefs[KEY_OBSOLETE_ATTEMPT_ID] = OWNER_N.toString()
            prefs[KEY_OBSOLETE_ROLLBACK_PATH] = "/transferred/arbitrary.db"
            prefs[KEY_ATTEMPT_ID] = OWNER_P.toString()
            prefs[KEY_ATTEMPT_EPOCH] = EPOCH_B.toString()
        }

        val read = repo.readProtocol()

        assertTrue(read is RestoreProtocolRead.Legacy)
        assertTrue((read as RestoreProtocolRead.Legacy).state.restoreInProgress)
        assertEquals(
            "/transferred/arbitrary.db",
            dataStore.data.first()[KEY_OBSOLETE_ROLLBACK_PATH],
        )
        assertEquals(0, files.undoFileReads)
    }

    @Test
    fun `partial released context still routes through the explicit legacy table`() = runTest {
        dataStore.edit { prefs ->
            prefs[KEY_LEGACY_BACKUP_SCHEMA] = 6
            prefs[KEY_LEGACY_BACKUP_CREATED_AT] = 10L
        }

        val read = repo.readProtocol()

        assertTrue(read is RestoreProtocolRead.Legacy)
        val legacy = (read as RestoreProtocolRead.Legacy).state
        assertFalse(legacy.restoreInProgress)
        assertNull(legacy.context)
    }

    @Test
    fun `installLegacyState atomically installs owned attempt and clears every released key`() =
        runTest {
            writeReleasedState(
                restoreInProgress = true,
                context = CONTEXT,
                available = true,
                originalDate = ORIGINAL_DATE,
            )
            dataStore.edit { prefs ->
                prefs[KEY_OBSOLETE_ROLLBACK_PATH] = "/cache/pre_restore_backup.db"
            }
            val legacy = repo.readProtocol() as RestoreProtocolRead.Legacy
            val attempt = restoreAttempt(
                owner = LegacyRestoreOwners.InterruptedAttempt,
                sourceRef = null,
            )

            assertTrue(repo.installLegacyState(legacy.epoch, attempt, activeUndo = null))

            val state = currentState()
            assertEquals(attempt, state.attempt)
            assertNull(state.activeUndo)
            val prefs = dataStore.data.first()
            assertEquals(EPOCH_A.toString(), prefs[KEY_INSTALL_EPOCH])
            assertEquals(EPOCH_A.toString(), prefs[KEY_ATTEMPT_EPOCH])
            assertNull(prefs[KEY_LEGACY_RESTORE_IN_PROGRESS])
            assertNull(prefs[KEY_LEGACY_PRE_RESTORE_AVAILABLE])
            assertNull(prefs[KEY_LEGACY_PRE_RESTORE_DATE])
            assertNull(prefs[KEY_OBSOLETE_ROLLBACK_PATH])
        }

    @Test
    fun `installLegacyState is replay safe but cannot overwrite evolved current state`() = runTest {
        writeReleasedState(
            restoreInProgress = false,
            context = null,
            available = true,
            originalDate = ORIGINAL_DATE,
        )
        val legacy = repo.readProtocol() as RestoreProtocolRead.Legacy
        val active = ActiveUndo(UndoRef(LegacyRestoreOwners.ActiveUndo), ORIGINAL_DATE)

        assertTrue(repo.installLegacyState(legacy.epoch, attempt = null, activeUndo = active))
        assertTrue(repo.installLegacyState(legacy.epoch, attempt = null, activeUndo = active))

        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        assertFalse(repo.installLegacyState(legacy.epoch, attempt = null, activeUndo = active))
        assertEquals(OWNER_N, currentState().attempt?.id)
    }

    @Test
    fun `installLegacyState rejects an epoch that is not owned by the recovery root`() = runTest {
        writeReleasedState(true, CONTEXT, available = false, originalDate = null)

        assertFalse(repo.installLegacyState(EPOCH_B, restoreAttempt(OWNER_N), null))

        assertTrue(repo.readProtocol() is RestoreProtocolRead.Legacy)
    }

    @Test
    fun `foreign envelope clears transferred protocol and released state before decoding`() =
        runTest {
            files.epoch = EPOCH_B
            activateUndo(OWNER_P, ORIGINAL_DATE)
            assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
            dataStore.edit { prefs -> prefs[KEY_LEGACY_PRE_RESTORE_AVAILABLE] = true }
            files.epoch = EPOCH_A

            val state = currentState()

            assertEquals(EPOCH_A, state.installEpoch)
            assertNull(state.attempt)
            assertNull(state.activeUndo)
            assertNull(state.terminalOutbox)
            val prefs = dataStore.data.first()
            assertEquals(EPOCH_A.toString(), prefs[KEY_INSTALL_EPOCH])
            assertNull(prefs[KEY_LEGACY_PRE_RESTORE_AVAILABLE])
            assertEquals(0, files.undoFileReads)
        }

    @Test
    fun `transferred A preferences reconcile under B without touching refs or the live db`() =
        runTest {
            val installAPreferences =
                File.createTempFile("restore-install-a", ".preferences_pb").also { it.delete() }
            val installBPreferences =
                File.createTempFile("restore-install-b", ".preferences_pb").also { it.delete() }
            val installBLiveDb = File.createTempFile("healthy-install-b", ".db")
            val liveBytes = "healthy-install-b-live-generation".encodeToByteArray()
            installBLiveDb.writeBytes(liveBytes)
            val installAScope = CoroutineScope(Dispatchers.IO + Job())
            val installBScope = CoroutineScope(Dispatchers.IO + Job())

            try {
                val installAStore = PreferenceDataStoreFactory.create(scope = installAScope) {
                    installAPreferences
                }
                val installAFiles = FakeRestoreRecoveryFiles(EPOCH_A)
                val installARepository = RestoreStateRepositoryImpl(installAStore, installAFiles)
                val activeP = ActiveUndo(UndoRef(OWNER_P), ORIGINAL_DATE)
                val completedP = restoreSucceeded(OWNER_P)
                assertTrue(installARepository.beginAttempt(restoreAttempt(OWNER_P)))
                assertTrue(installARepository.recordAttemptCommitted(OWNER_P))
                assertTrue(
                    installARepository.finalizeAttempt(
                        OWNER_P,
                        ActiveUndoTransition.Replace(activeP),
                        completedP,
                    ),
                )
                assertTrue(installARepository.acknowledgeTerminal(OWNER_P))
                val unresolvedN = restoreAttempt(OWNER_N)
                assertTrue(installARepository.beginAttempt(unresolvedN))
                installAStore.edit { prefs ->
                    prefs[KEY_TERMINAL_EPOCH] = EPOCH_A.toString()
                    prefs[KEY_TERMINAL_OWNER] = OWNER_OTHER.toString()
                    prefs[KEY_TERMINAL_TYPE] = "RestoreSucceeded"
                    prefs[KEY_TERMINAL_RESTORED_AT] = RESTORED_AT
                    prefs[KEY_TERMINAL_PREVIOUS_AVAILABLE] = true
                }
                val installAState =
                    (installARepository.readProtocol() as RestoreProtocolRead.Current).state
                assertEquals(unresolvedN, installAState.attempt)
                assertEquals(activeP, installAState.activeUndo)
                assertEquals(OWNER_OTHER, installAState.terminalOutbox?.owner)

                installAScope.cancel()
                installAPreferences.copyTo(installBPreferences, overwrite = true)

                val installBStore = PreferenceDataStoreFactory.create(scope = installBScope) {
                    installBPreferences
                }
                val installBFiles = FakeRestoreRecoveryFiles(EPOCH_B)
                val installBRepository = RestoreStateRepositoryImpl(installBStore, installBFiles)

                val installBState =
                    (installBRepository.readProtocol() as RestoreProtocolRead.Current).state

                assertEquals(EPOCH_B, installBState.installEpoch)
                assertNull(installBState.attempt)
                assertNull(installBState.activeUndo)
                assertNull(installBState.terminalOutbox)
                val installBPrefs = installBStore.data.first()
                assertEquals(EPOCH_B.toString(), installBPrefs[KEY_INSTALL_EPOCH])
                assertNull(installBPrefs[KEY_ATTEMPT_ID])
                assertNull(installBPrefs[KEY_ACTIVE_UNDO_EPOCH])
                assertNull(installBPrefs[KEY_TERMINAL_EPOCH])
                assertEquals(0, installBFiles.undoFileReads)
                assertEquals(0, installBFiles.restoreSourceFileReads)
                assertArrayEquals(liveBytes, installBLiveDb.readBytes())
            } finally {
                installAScope.cancel()
                installBScope.cancel()
                installAPreferences.delete()
                installBPreferences.delete()
                installBLiveDb.delete()
            }
        }

    @Test
    fun `foreign envelope remains foreign even when a same-named local undo exists`() = runTest {
        files.epoch = EPOCH_B
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        files.existingUndo[UndoRef(OWNER_N)] = File(tempFile.absolutePath + ".undo_${OWNER_N}.db")
        files.epoch = EPOCH_A

        val state = currentState()

        assertNull(state.attempt)
        assertEquals(0, files.undoFileReads)
    }

    @Test
    fun `same epoch missing undo remains a real unresolved local attempt`() = runTest {
        val attempt = restoreAttempt(OWNER_N)
        assertTrue(repo.beginAttempt(attempt))
        assertNull(files.undoFile(UndoRef(OWNER_N)))
        files.undoFileReads = 0

        val state = currentState()

        assertEquals(attempt, state.attempt)
        assertEquals(0, files.undoFileReads, "state decoding never turns a missing file into foreign")
    }

    @Test
    fun `foreign attempt record clears the whole mixed epoch envelope`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        dataStore.edit { prefs -> prefs[KEY_ATTEMPT_EPOCH] = EPOCH_B.toString() }

        val state = currentState()

        assertNull(state.attempt)
        assertNull(state.activeUndo)
        assertNull(state.terminalOutbox)
        assertEquals(EPOCH_A.toString(), dataStore.data.first()[KEY_INSTALL_EPOCH])
    }

    @Test
    fun `foreign pointer record clears the whole mixed epoch envelope`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val attempt = restoreAttempt(OWNER_N)
        assertTrue(repo.beginAttempt(attempt))
        dataStore.edit { prefs -> prefs[KEY_ACTIVE_UNDO_EPOCH] = EPOCH_B.toString() }

        val state = currentState()

        assertNull(state.attempt)
        assertNull(state.activeUndo)
        assertNull(state.terminalOutbox)
    }

    @Test
    fun `foreign terminal record clears the whole mixed epoch envelope`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val terminal = restoreSucceeded(OWNER_N)
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        seedTerminal(terminal)
        dataStore.edit { prefs -> prefs[KEY_TERMINAL_EPOCH] = EPOCH_B.toString() }

        val state = currentState()

        assertNull(state.terminalOutbox)
        assertNull(state.activeUndo)
        assertNull(state.attempt)
    }

    @Test
    fun `record payload without its record epoch is same-install corruption`() = runTest {
        currentState()
        dataStore.edit { prefs ->
            prefs[KEY_ATTEMPT_ID] = OWNER_N.toString()
            prefs[KEY_ATTEMPT_TYPE] = "Restore"
            prefs[KEY_ATTEMPT_PHASE] = RestoreAttempt.Phase.Prepared.name
        }

        val read = repo.readProtocol()

        assertTrue(read is RestoreProtocolRead.Corrupt)
        assertTrue((read as RestoreProtocolRead.Corrupt).reason.contains("epoch is missing"))
        assertEquals(OWNER_N.toString(), dataStore.data.first()[KEY_ATTEMPT_ID])
    }

    @Test
    fun `restore and rollback descriptors round trip only opaque owner identities`() = runTest {
        val restore = restoreAttempt(OWNER_N)
        assertTrue(repo.beginAttempt(restore))
        assertEquals(restore, currentState().attempt)
        assertFalse(dataStore.data.first().asMap().keys.any { it.name.contains("path") })

        assertTrue(repo.discardPreparedAttempt(OWNER_N))
        val rollback = rollbackAttempt(OWNER_ROLLBACK, UndoRef(OWNER_P))
        assertTrue(repo.beginAttempt(rollback))
        assertEquals(rollback, currentState().attempt)
    }

    @Test
    fun `beginAttempt is exact-idempotent and rejects owner or descriptor disagreement`() = runTest {
        val first = restoreAttempt(OWNER_N)
        assertTrue(repo.beginAttempt(first))

        assertTrue(repo.beginAttempt(first))
        assertFalse(repo.beginAttempt(restoreAttempt(OWNER_OTHER)))
        assertFalse(repo.beginAttempt(first.copy(context = CONTEXT.copy(backupSchemaVersion = 9))))
        assertEquals(first, currentState().attempt)
    }

    @Test
    fun `user undo admission atomically rejects a ref made stale after caller preflight`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val staleRollback = rollbackAttempt(
            owner = OWNER_ROLLBACK,
            source = UndoRef(OWNER_P),
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
        )
        activateUndo(OWNER_N, NEW_DATE)

        assertFalse(repo.beginAttempt(staleRollback))

        val state = currentState()
        assertNull(state.attempt)
        assertEquals(UndoRef(OWNER_N), state.activeUndo?.ref)
    }

    @Test
    fun `owned user undo admission remains exact-idempotent`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val rollback = rollbackAttempt(
            owner = OWNER_ROLLBACK,
            source = UndoRef(OWNER_P),
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
        )

        assertTrue(repo.beginAttempt(rollback))
        assertTrue(repo.beginAttempt(rollback))
        assertEquals(rollback, currentState().attempt)
    }

    @Test
    fun `recordAttemptCommitted advances only the exact owner and is replay safe`() = runTest {
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))

        assertFalse(repo.recordAttemptCommitted(OWNER_OTHER))
        assertEquals(RestoreAttempt.Phase.Prepared, currentState().attempt?.phase)
        assertTrue(repo.recordAttemptCommitted(OWNER_N))
        assertTrue(repo.recordAttemptCommitted(OWNER_N))
        assertEquals(RestoreAttempt.Phase.Committed, currentState().attempt?.phase)
    }

    @Test
    fun `beginCompensation atomically replaces only its restore with exact rollback`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        val rollback = rollbackAttempt(OWNER_ROLLBACK, UndoRef(OWNER_N))

        assertFalse(repo.beginCompensation(OWNER_OTHER, rollback))
        assertFalse(
            repo.beginCompensation(
                OWNER_N,
                rollback.copy(sourceRef = UndoRef(OWNER_P)),
            ),
        )
        assertFalse(
            repo.beginCompensation(
                OWNER_N,
                rollback.copy(origin = RestoreAttempt.RollbackOrigin.UserUndo),
            ),
        )
        assertTrue(repo.beginCompensation(OWNER_N, rollback))

        val state = currentState()
        assertEquals(rollback, state.attempt)
        assertEquals(UndoRef(OWNER_P), state.activeUndo?.ref)
        assertNull(state.terminalOutbox)
    }

    @Test
    fun `beginCompensation rejects reuse of the restore owner`() = runTest {
        val restore = restoreAttempt(OWNER_N)
        assertTrue(repo.beginAttempt(restore))
        val reusedOwner = rollbackAttempt(OWNER_N, UndoRef(OWNER_N))

        assertFalse(repo.beginCompensation(OWNER_N, reusedOwner))

        assertEquals(restore, currentState().attempt)
    }

    @Test
    fun `discardPreparedAttempt removes only owned Prepared journal and preserves pointer`() =
        runTest {
            activateUndo(OWNER_P, ORIGINAL_DATE)
            assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))

            assertFalse(repo.discardPreparedAttempt(OWNER_OTHER))
            assertTrue(repo.discardPreparedAttempt(OWNER_N))

            val state = currentState()
            assertNull(state.attempt)
            assertEquals(UndoRef(OWNER_P), state.activeUndo?.ref)
        }

    @Test
    fun `discardPreparedAttempt refuses a Committed journal`() = runTest {
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        assertTrue(repo.recordAttemptCommitted(OWNER_N))

        assertFalse(repo.discardPreparedAttempt(OWNER_N))
        assertEquals(RestoreAttempt.Phase.Committed, currentState().attempt?.phase)
    }

    @Test
    fun `verified restore finalization atomically installs N outbox and removes journal`() =
        runTest {
            activateUndo(OWNER_P, ORIGINAL_DATE)
            val attempt = restoreAttempt(OWNER_N)
            val activeN = ActiveUndo(UndoRef(OWNER_N), NEW_DATE)
            val terminal = restoreSucceeded(OWNER_N)
            assertTrue(repo.beginAttempt(attempt))
            assertTrue(repo.recordAttemptCommitted(OWNER_N))

            assertTrue(
                repo.finalizeAttempt(
                    OWNER_N,
                    ActiveUndoTransition.Replace(activeN),
                    terminal,
                ),
            )

            val state = currentState()
            assertNull(state.attempt)
            assertEquals(activeN, state.activeUndo)
            assertEquals(terminal, state.terminalOutbox)
            val prefs = dataStore.data.first()
            assertEquals(EPOCH_A.toString(), prefs[KEY_ACTIVE_UNDO_EPOCH])
            assertEquals(EPOCH_A.toString(), prefs[KEY_TERMINAL_EPOCH])
        }

    @Test
    fun `verified restore with missing N atomically clears old pointer instead of advertising P`() =
        runTest {
            activateUndo(OWNER_P, ORIGINAL_DATE)
            assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
            assertTrue(repo.recordAttemptCommitted(OWNER_N))
            val terminal = restoreSucceeded(OWNER_N, previousAvailable = false)

            assertTrue(
                repo.finalizeAttempt(
                    OWNER_N,
                    ActiveUndoTransition.Replace(null),
                    terminal,
                ),
            )

            val state = currentState()
            assertNull(state.activeUndo)
            assertEquals(terminal, state.terminalOutbox)
            assertNull(state.attempt)
        }

    @Test
    fun `finalization before commit or with wrong pointer transition preserves all old truth`() =
        runTest {
            activateUndo(OWNER_P, ORIGINAL_DATE)
            val attempt = restoreAttempt(OWNER_N)
            assertTrue(repo.beginAttempt(attempt))
            val terminal = restoreSucceeded(OWNER_N)

            assertFalse(
                repo.finalizeAttempt(
                    OWNER_N,
                    ActiveUndoTransition.Replace(ActiveUndo(UndoRef(OWNER_N), NEW_DATE)),
                    terminal,
                ),
            )
            assertTrue(repo.recordAttemptCommitted(OWNER_N))
            assertFalse(
                repo.finalizeAttempt(
                    OWNER_N,
                    ActiveUndoTransition.Replace(ActiveUndo(UndoRef(OWNER_P), ORIGINAL_DATE)),
                    terminal,
                ),
            )

            val state = currentState()
            assertEquals(attempt.copy(phase = RestoreAttempt.Phase.Committed), state.attempt)
            assertEquals(UndoRef(OWNER_P), state.activeUndo?.ref)
            assertNull(state.terminalOutbox)
        }

    @Test
    fun `restore finalization rejects terminal availability that disagrees with the pointer`() =
        runTest {
            activateUndo(OWNER_P, ORIGINAL_DATE)
            val attempt = restoreAttempt(OWNER_N)
            assertTrue(repo.beginAttempt(attempt))
            assertTrue(repo.recordAttemptCommitted(OWNER_N))
            val activeN = ActiveUndo(UndoRef(OWNER_N), NEW_DATE)

            assertFalse(
                repo.finalizeAttempt(
                    OWNER_N,
                    ActiveUndoTransition.Replace(null),
                    restoreSucceeded(OWNER_N, previousAvailable = true),
                ),
            )
            assertFalse(
                repo.finalizeAttempt(
                    OWNER_N,
                    ActiveUndoTransition.Replace(activeN),
                    restoreSucceeded(OWNER_N, previousAvailable = false),
                ),
            )

            val state = currentState()
            assertEquals(attempt.copy(phase = RestoreAttempt.Phase.Committed), state.attempt)
            assertEquals(UndoRef(OWNER_P), state.activeUndo?.ref)
            assertNull(state.terminalOutbox)
        }

    @Test
    fun `scenario-one rollback rejects a user-undo terminal`() = runTest {
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        val rollback = rollbackAttempt(OWNER_ROLLBACK, UndoRef(OWNER_N))
        assertTrue(repo.beginCompensation(OWNER_N, rollback))
        assertTrue(repo.recordAttemptCommitted(OWNER_ROLLBACK))

        assertFalse(
            repo.finalizeAttempt(
                OWNER_ROLLBACK,
                ActiveUndoTransition.ClearIf(UndoRef(OWNER_N)),
                RestoreTerminal.UndoSucceeded(OWNER_ROLLBACK),
            ),
        )

        assertEquals(
            rollback.copy(phase = RestoreAttempt.Phase.Committed),
            currentState().attempt,
        )
    }

    @Test
    fun `user rollback rejects a restore-failure terminal`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val rollback = rollbackAttempt(
            owner = OWNER_ROLLBACK,
            source = UndoRef(OWNER_P),
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
        )
        assertTrue(repo.beginAttempt(rollback))
        assertTrue(repo.recordAttemptCommitted(OWNER_ROLLBACK))

        assertFalse(
            repo.finalizeAttempt(
                OWNER_ROLLBACK,
                ActiveUndoTransition.ClearIf(UndoRef(OWNER_P)),
                RestoreTerminal.RestoreFailed(OWNER_ROLLBACK, BackupErrorCode.Unknown),
            ),
        )

        val state = currentState()
        assertEquals(rollback.copy(phase = RestoreAttempt.Phase.Committed), state.attempt)
        assertEquals(UndoRef(OWNER_P), state.activeUndo?.ref)
        assertNull(state.terminalOutbox)
    }

    @Test
    fun `user rollback clears only the exact active ref it applied`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val rollback = rollbackAttempt(
            owner = OWNER_ROLLBACK,
            source = UndoRef(OWNER_P),
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
        )
        assertTrue(repo.beginAttempt(rollback))
        assertTrue(repo.recordAttemptCommitted(OWNER_ROLLBACK))
        val terminal = RestoreTerminal.UndoSucceeded(OWNER_ROLLBACK)

        assertTrue(
            repo.finalizeAttempt(
                OWNER_ROLLBACK,
                ActiveUndoTransition.ClearIf(UndoRef(OWNER_P)),
                terminal,
            ),
        )

        val state = currentState()
        assertNull(state.attempt)
        assertNull(state.activeUndo)
        assertEquals(terminal, state.terminalOutbox)
    }

    @Test
    fun `user rollback finalization rejects an active ref changed after claim`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        val rollback = rollbackAttempt(
            owner = OWNER_ROLLBACK,
            source = UndoRef(OWNER_P),
            origin = RestoreAttempt.RollbackOrigin.UserUndo,
        )
        assertTrue(repo.beginAttempt(rollback))
        assertTrue(repo.recordAttemptCommitted(OWNER_ROLLBACK))
        dataStore.edit { prefs -> prefs[KEY_ACTIVE_UNDO_REF] = OWNER_N.toString() }

        assertFalse(
            repo.finalizeAttempt(
                OWNER_ROLLBACK,
                ActiveUndoTransition.ClearIf(UndoRef(OWNER_P)),
                RestoreTerminal.UndoSucceeded(OWNER_ROLLBACK),
            ),
        )

        val state = currentState()
        assertEquals(rollback.copy(phase = RestoreAttempt.Phase.Committed), state.attempt)
        assertEquals(UndoRef(OWNER_N), state.activeUndo?.ref)
        assertNull(state.terminalOutbox)
    }

    @Test
    fun `compensation applying N preserves unrelated active P`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        val rollback = rollbackAttempt(OWNER_ROLLBACK, UndoRef(OWNER_N))
        assertTrue(repo.beginCompensation(OWNER_N, rollback))
        assertTrue(repo.recordAttemptCommitted(OWNER_ROLLBACK))
        val terminal = RestoreTerminal.RestoreFailed(
            owner = OWNER_ROLLBACK,
            reason = BackupErrorCode.Io,
        )

        assertTrue(
            repo.finalizeAttempt(
                OWNER_ROLLBACK,
                ActiveUndoTransition.ClearIf(UndoRef(OWNER_N)),
                terminal,
            ),
        )

        val state = currentState()
        assertEquals(UndoRef(OWNER_P), state.activeUndo?.ref)
        assertEquals(terminal, state.terminalOutbox)
        assertNull(state.attempt)
    }

    @Test
    fun `finalization replay succeeds only for the already persisted matching outbox`() = runTest {
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        assertTrue(repo.recordAttemptCommitted(OWNER_N))
        val terminal = restoreSucceeded(OWNER_N, previousAvailable = false)
        val transition = ActiveUndoTransition.Replace(null)
        assertTrue(repo.finalizeAttempt(OWNER_N, transition, terminal))

        assertTrue(repo.finalizeAttempt(OWNER_N, transition, terminal))
        assertFalse(
            repo.finalizeAttempt(
                OWNER_N,
                transition,
                terminal.copy(restoredAtEpochMs = terminal.restoredAtEpochMs + 1),
            ),
        )
    }

    @Test
    fun `acknowledgeTerminal clears only the matching owner after persisted publication`() = runTest {
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        assertTrue(repo.recordAttemptCommitted(OWNER_N))
        val terminal = restoreSucceeded(OWNER_N, previousAvailable = false)
        assertTrue(repo.finalizeAttempt(OWNER_N, ActiveUndoTransition.Replace(null), terminal))

        assertFalse(repo.acknowledgeTerminal(OWNER_OTHER))
        assertEquals(terminal, currentState().terminalOutbox)
        assertTrue(repo.acknowledgeTerminal(OWNER_N))
        assertNull(currentState().terminalOutbox)
        assertFalse(repo.acknowledgeTerminal(OWNER_N))
    }

    @Test
    fun `pending terminal prevents a new attempt from overwriting the one-slot outbox`() = runTest {
        assertTrue(repo.beginAttempt(restoreAttempt(OWNER_N)))
        assertTrue(repo.recordAttemptCommitted(OWNER_N))
        val terminal = restoreSucceeded(OWNER_N, previousAvailable = false)
        assertTrue(repo.finalizeAttempt(OWNER_N, ActiveUndoTransition.Replace(null), terminal))

        assertFalse(repo.beginAttempt(restoreAttempt(OWNER_OTHER)))
        assertEquals(terminal, currentState().terminalOutbox)
    }

    @Test
    fun `abandonInterruptedAttempt clears owned restore and all untruthful pointer and outbox state`() =
        runTest {
            activateUndo(OWNER_P, ORIGINAL_DATE)
            assertTrue(
                repo.beginAttempt(
                    restoreAttempt(LegacyRestoreOwners.InterruptedAttempt)
                        .copy(undoRef = null, sourceRef = null),
                ),
            )
            seedTerminal(restoreSucceeded(OWNER_OTHER))

            assertFalse(repo.abandonInterruptedAttempt(OWNER_OTHER))
            assertTrue(
                repo.abandonInterruptedAttempt(LegacyRestoreOwners.InterruptedAttempt),
            )

            val state = currentState()
            assertNull(state.attempt)
            assertNull(state.activeUndo)
            assertNull(state.terminalOutbox)
        }

    @Test
    fun `abandonInterruptedAttempt refuses a rollback journal`() = runTest {
        val rollback = rollbackAttempt(OWNER_ROLLBACK, UndoRef(OWNER_P))
        assertTrue(repo.beginAttempt(rollback))

        assertFalse(repo.abandonInterruptedAttempt(OWNER_ROLLBACK))
        assertEquals(rollback, currentState().attempt)
    }

    @Test
    fun `observeActiveUndo is epoch filtered and never exposes a foreign pointer`() = runTest {
        activateUndo(OWNER_P, ORIGINAL_DATE)
        assertEquals(UndoRef(OWNER_P), repo.observeActiveUndo().first()?.ref)

        dataStore.edit { prefs -> prefs[KEY_ACTIVE_UNDO_EPOCH] = EPOCH_B.toString() }

        assertNull(repo.observeActiveUndo().first())
        assertNull(currentState().activeUndo)
    }

    private suspend fun currentState(): RestoreProtocolState {
        val read = repo.readProtocol()
        assertTrue(read is RestoreProtocolRead.Current, "expected Current but was $read")
        return (read as RestoreProtocolRead.Current).state
    }

    private suspend fun activateUndo(owner: RestoreOwnerId, date: Long) {
        val attempt = restoreAttempt(owner)
        val terminal = restoreSucceeded(owner)
        assertTrue(repo.beginAttempt(attempt))
        assertTrue(repo.recordAttemptCommitted(owner))
        assertTrue(
            repo.finalizeAttempt(
                owner,
                ActiveUndoTransition.Replace(ActiveUndo(UndoRef(owner), date)),
                terminal,
            ),
        )
        assertTrue(repo.acknowledgeTerminal(owner))
    }

    private suspend fun seedTerminal(terminal: RestoreTerminal.RestoreSucceeded) {
        dataStore.edit { prefs ->
            prefs[KEY_TERMINAL_EPOCH] = files.epoch.toString()
            prefs[KEY_TERMINAL_OWNER] = terminal.owner.toString()
            prefs[KEY_TERMINAL_TYPE] = "RestoreSucceeded"
            prefs[KEY_TERMINAL_RESTORED_AT] = terminal.restoredAtEpochMs
            prefs[KEY_TERMINAL_PREVIOUS_AVAILABLE] = terminal.previousVersionAvailable
        }
    }

    private suspend fun writeReleasedState(
        restoreInProgress: Boolean,
        context: RestoreInProgressContext?,
        available: Boolean,
        originalDate: Long?,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_LEGACY_RESTORE_IN_PROGRESS] = restoreInProgress
            context?.let {
                prefs[KEY_LEGACY_BACKUP_SCHEMA] = it.backupSchemaVersion
                prefs[KEY_LEGACY_BACKUP_CREATED_AT] = it.backupCreatedAtEpochMs
                prefs[KEY_LEGACY_BACKUP_APP_VERSION] = it.backupAppVersion
                prefs[KEY_LEGACY_RESTORE_STARTED_AT] = it.startedAtEpochMs
            }
            prefs[KEY_LEGACY_PRE_RESTORE_AVAILABLE] = available
            originalDate?.let { prefs[KEY_LEGACY_PRE_RESTORE_DATE] = it }
        }
    }

    private fun restoreAttempt(
        owner: RestoreOwnerId,
        sourceRef: RestoreSourceRef? = RestoreSourceRef(owner),
    ): RestoreAttempt.Restore = RestoreAttempt.Restore(
        id = owner,
        phase = RestoreAttempt.Phase.Prepared,
        context = CONTEXT,
        undoRef = UndoRef(owner),
        sourceRef = sourceRef,
    )

    private fun rollbackAttempt(
        owner: RestoreOwnerId,
        source: UndoRef,
        origin: RestoreAttempt.RollbackOrigin =
            RestoreAttempt.RollbackOrigin.ScenarioOneRecovery,
    ): RestoreAttempt.Rollback = RestoreAttempt.Rollback(
        id = owner,
        phase = RestoreAttempt.Phase.Prepared,
        sourceRef = source,
        origin = origin,
    )

    private fun restoreSucceeded(
        owner: RestoreOwnerId,
        previousAvailable: Boolean = true,
    ): RestoreTerminal.RestoreSucceeded = RestoreTerminal.RestoreSucceeded(
        owner = owner,
        restoredAtEpochMs = RESTORED_AT,
        previousVersionAvailable = previousAvailable,
    )

    private class FakeRestoreRecoveryFiles(
        var epoch: InstallEpoch,
    ) : RestoreRecoveryFiles {

        var undoFileReads: Int = 0
        var restoreSourceFileReads: Int = 0
        val existingUndo = mutableMapOf<UndoRef, File>()

        override suspend fun installEpoch(): InstallEpoch = epoch

        override suspend fun publishUndo(source: File, ref: UndoRef): BackupResult<File> =
            error("not used")

        override suspend fun publishRestoreSource(
            source: File,
            ref: RestoreSourceRef,
        ): BackupResult<File> = error("not used")

        override fun undoFile(ref: UndoRef): File? {
            undoFileReads += 1
            return existingUndo[ref]
        }

        override fun restoreSourceFile(ref: RestoreSourceRef): File? {
            restoreSourceFileReads += 1
            return null
        }

        override fun legacyPreRestoreFile(): File? = error("not used")

        override suspend fun migrateLegacyUndo(ref: UndoRef): BackupResult<File> =
            error("not used")

        override suspend fun deleteUndo(ref: UndoRef): Boolean = error("not used")

        override suspend fun deleteRestoreSource(ref: RestoreSourceRef): Boolean =
            error("not used")

        override suspend fun publishRecoveryExport(source: File): BackupResult<File> =
            error("not used")

        override fun recoveryExportFile(): File? = error("not used")

        override suspend fun createShareCopy(
            source: File,
            fileName: String,
        ): BackupResult<File> = error("not used")

        override suspend fun sweep(
            state: RestoreProtocolState,
        ): RestoreGarbageCollectionReport = error("not used")
    }

    private companion object {
        const val PREFS_FILE_NAME = "restore_state_prefs"
        const val ORIGINAL_DATE = 1_700_000_000_000L
        const val NEW_DATE = 1_710_000_000_000L
        const val RESTORED_AT = 1_720_000_000_000L

        val EPOCH_A = InstallEpoch(
            RestoreOwnerId("11111111-1111-4111-8111-111111111111"),
        )
        val EPOCH_B = InstallEpoch(
            RestoreOwnerId("22222222-2222-4222-8222-222222222222"),
        )
        val OWNER_P = RestoreOwnerId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        val OWNER_N = RestoreOwnerId("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
        val OWNER_ROLLBACK = RestoreOwnerId("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
        val OWNER_OTHER = RestoreOwnerId("dddddddd-dddd-4ddd-8ddd-dddddddddddd")
        val CONTEXT = RestoreInProgressContext(
            backupSchemaVersion = 6,
            backupCreatedAtEpochMs = 1_690_000_000_000L,
            backupAppVersion = "1.2.3",
            startedAtEpochMs = 1_700_000_000_000L,
        )

        // Wire keys are duplicated intentionally: any accidental rename must fail persisted tests.
        val KEY_INSTALL_EPOCH = stringPreferencesKey("restore_protocol_install_epoch")
        val KEY_ATTEMPT_EPOCH = stringPreferencesKey("restore_protocol_attempt_epoch")
        val KEY_ATTEMPT_ID = stringPreferencesKey("restore_protocol_attempt_id")
        val KEY_ATTEMPT_TYPE = stringPreferencesKey("restore_protocol_attempt_type")
        val KEY_ATTEMPT_PHASE = stringPreferencesKey("restore_protocol_attempt_phase")
        val KEY_ACTIVE_UNDO_EPOCH = stringPreferencesKey("restore_protocol_active_undo_epoch")
        val KEY_ACTIVE_UNDO_REF = stringPreferencesKey("restore_protocol_active_undo_ref")
        val KEY_TERMINAL_EPOCH = stringPreferencesKey("restore_protocol_terminal_outbox_epoch")
        val KEY_TERMINAL_OWNER = stringPreferencesKey("restore_protocol_terminal_outbox_owner")
        val KEY_TERMINAL_TYPE = stringPreferencesKey("restore_protocol_terminal_outbox_type")
        val KEY_TERMINAL_RESTORED_AT =
            longPreferencesKey("restore_protocol_terminal_restored_at_epoch_ms")
        val KEY_TERMINAL_PREVIOUS_AVAILABLE =
            booleanPreferencesKey("restore_protocol_terminal_previous_version_available")

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

        val KEY_OBSOLETE_ATTEMPT_ID = stringPreferencesKey("restore_attempt_id")
        val KEY_OBSOLETE_ROLLBACK_PATH =
            stringPreferencesKey("restore_attempt_rollback_snapshot_path")
    }
}
