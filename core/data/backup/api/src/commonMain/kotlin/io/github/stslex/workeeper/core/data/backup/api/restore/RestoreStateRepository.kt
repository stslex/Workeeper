// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

import kotlinx.coroutines.flow.Flow

/** Epoch-reconciled restore state and replay-safe terminal-outbox transaction owner. */
interface RestoreStateRepository {

    /** Reconciles installation ownership before decoding any attempt, ref, pointer or outbox. */
    suspend fun readProtocol(): RestoreProtocolRead

    /** Installs the explicit released-state migration result in one edit. */
    suspend fun installLegacyState(
        epoch: InstallEpoch,
        attempt: RestoreAttempt?,
        activeUndo: ActiveUndo?,
    ): Boolean

    /** Atomically claims the attempt slot; false when another unresolved owner holds it. */
    suspend fun beginAttempt(attempt: RestoreAttempt): Boolean

    /** Advances the owned attempt to Committed; false unless [attemptId] owns the slot. */
    suspend fun recordAttemptCommitted(attemptId: RestoreOwnerId): Boolean

    /** Atomically replaces one owned restore journal with its exact compensation rollback. */
    suspend fun beginCompensation(
        restoreAttemptId: RestoreOwnerId,
        rollback: RestoreAttempt.Rollback,
    ): Boolean

    /** Drops only an owned Prepared attempt after a proven pre-PONR rejection. */
    suspend fun discardPreparedAttempt(attemptId: RestoreOwnerId): Boolean

    /**
     * One owner-checked edit: transition the pointer, write terminal outbox, remove attempt.
     * This is the shared finalizer for cold-start and in-process candidate preflight.
     */
    suspend fun finalizeAttempt(
        attemptId: RestoreOwnerId,
        activeUndoTransition: ActiveUndoTransition,
        terminal: RestoreTerminal,
    ): Boolean

    /** Clears the matching outbox only after its AppDialog publication returned successfully. */
    suspend fun acknowledgeTerminal(owner: RestoreOwnerId): Boolean

    /** Explicit user escape: atomically abandon only [attemptId] and all now-untruthful state. */
    suspend fun abandonInterruptedAttempt(attemptId: RestoreOwnerId): Boolean

    /** Epoch-filtered reactive pointer used by Settings. Foreign state never emits as available. */
    fun observeActiveUndo(): Flow<ActiveUndo?>
}
