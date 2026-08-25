// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode

/** Validated installation or transaction owner. The UUID spelling is persisted wire format. */
data class RestoreOwnerId(val value: String) {

    init {
        require(OWNER_PATTERN.matches(value)) { "restore owner must be a lower-case UUID" }
    }

    override fun toString(): String = value

    private companion object {
        val OWNER_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
        )
    }
}

/** Stable random token owned by this installation's no-backup recovery root. */
data class InstallEpoch(val value: RestoreOwnerId) {
    override fun toString(): String = value.toString()
}

/** Opaque immutable undo identity. Its path is always derived by the Android storage owner. */
data class UndoRef(val owner: RestoreOwnerId) {
    override fun toString(): String = owner.toString()
}

/** Opaque runtime-owned staged restore identity. No caller-supplied path is persisted. */
data class RestoreSourceRef(val owner: RestoreOwnerId) {
    override fun toString(): String = owner.toString()
}

/** One persisted replacement attempt. Exactly one unresolved owner may advance or finalize it. */
sealed interface RestoreAttempt {

    val id: RestoreOwnerId
    val phase: Phase

    data class Restore(
        override val id: RestoreOwnerId,
        override val phase: Phase,
        val context: RestoreInProgressContext?,
        /** Null only for the explicit legacy marker + missing-C rollout case. */
        val undoRef: UndoRef?,
        /** Null only for a legacy attempt created before runtime source ownership existed. */
        val sourceRef: RestoreSourceRef?,
    ) : RestoreAttempt

    data class Rollback(
        override val id: RestoreOwnerId,
        override val phase: Phase,
        /** Exact immutable image this rollback applies. */
        val sourceRef: UndoRef,
        val origin: RollbackOrigin,
    ) : RestoreAttempt

    /** [Committed] alone permits verified finalization; [Prepared] is outcome-unknown. */
    enum class Phase {
        Prepared,
        Committed,
    }

    /** Durable discriminator of a rollback's user-facing terminal. Names are wire format. */
    enum class RollbackOrigin {
        UserUndo,
        ScenarioOneRecovery,
    }
}

/** The one undo opportunity currently advertised to the user. */
data class ActiveUndo(
    val ref: UndoRef,
    val originalDataDateEpochMs: Long,
)

/** Replayable terminal payload written atomically with pointer transition and attempt removal. */
sealed interface RestoreTerminal {

    val owner: RestoreOwnerId

    data class RestoreSucceeded(
        override val owner: RestoreOwnerId,
        val restoredAtEpochMs: Long,
        val previousVersionAvailable: Boolean,
    ) : RestoreTerminal

    data class RestoreFailed(
        override val owner: RestoreOwnerId,
        val reason: BackupErrorCode,
    ) : RestoreTerminal

    data class UndoSucceeded(
        override val owner: RestoreOwnerId,
    ) : RestoreTerminal
}

/** Installation-scoped restore truth stored in one dedicated DataStore edit domain. */
data class RestoreProtocolState(
    val installEpoch: InstallEpoch,
    val attempt: RestoreAttempt?,
    val activeUndo: ActiveUndo?,
    val terminalOutbox: RestoreTerminal?,
)

/** Released positional state, read only while no protocol epoch has ever been installed. */
data class LegacyRestoreState(
    val restoreInProgress: Boolean,
    val context: RestoreInProgressContext?,
    val preRestoreBackupAvailable: Boolean,
    val preRestoreOriginalDateEpochMs: Long?,
)

/** Result of epoch reconciliation and persisted state decoding. */
sealed interface RestoreProtocolRead {

    data class Current(val state: RestoreProtocolState) : RestoreProtocolRead

    data class Legacy(val epoch: InstallEpoch, val state: LegacyRestoreState) : RestoreProtocolRead

    /** Same-install state that cannot be decoded safely; no path has been dereferenced. */
    data class Corrupt(val epoch: InstallEpoch, val reason: String) : RestoreProtocolRead
}

/** Atomic pointer policy applied by the shared verified-attempt finalizer. */
sealed interface ActiveUndoTransition {

    /** A verified restore replaces the advertised pointer with [activeUndo], including null. */
    data class Replace(val activeUndo: ActiveUndo?) : ActiveUndoTransition

    /** A rollback clears only the exact immutable ref it applied. */
    data class ClearIf(val appliedRef: UndoRef) : ActiveUndoTransition
}

/** Stable synthetic owners used only by the explicit released-state migration table. */
object LegacyRestoreOwners {
    val InterruptedAttempt = RestoreOwnerId("00000000-0000-4000-8000-000000000001")
    val ActiveUndo = RestoreOwnerId("00000000-0000-4000-8000-000000000002")
}
