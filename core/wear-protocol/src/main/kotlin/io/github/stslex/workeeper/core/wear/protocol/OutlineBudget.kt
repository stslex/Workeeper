// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

/**
 * wear-phase-2-exercise-list.md §5.7 (P15): an `Active` outline is sent only while its snapshot
 * response, with the widest value of every authority field, fits [WearProtocol.OUTLINE_BUDGET_BYTES];
 * the decision does not depend on the lease. Every response path builds its replacement through
 * [bounded], so a command response for an outline it keeps always fits the envelope.
 */
object OutlineBudget {

    /** The outline of [snapshot], or `PayloadTooLarge` of its session when the outline does not fit. */
    fun bounded(snapshot: SnapshotData): SnapshotData {
        val active = snapshot.payload as? SnapshotPayload.Active ?: return snapshot
        if (fits(snapshot)) return snapshot
        return snapshot.copy(payload = SnapshotPayload.PayloadTooLarge(active.sessionUuid, active.sessionRevision))
    }

    /** True for every payload but an `Active` outline whose widest snapshot response exceeds the budget. */
    fun fits(snapshot: SnapshotData): Boolean {
        val active = snapshot.payload as? SnapshotPayload.Active ?: return true
        val widest = ActiveWorkoutSnapshotResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = WIDEST_UUID,
            snapshot = snapshot.copy(payload = active.copy(mutationAuthority = WIDEST_AUTHORITY)),
        )
        return WearProtocolCodec.serialize(widest).size <= WearProtocol.OUTLINE_BUDGET_BYTES
    }

    /** A canonical UUID: every one has the same 36 characters. */
    private val WIDEST_UUID = CanonicalUuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")

    /** A 19-digit lease generation and the full 120,000 ms window. */
    private val WIDEST_AUTHORITY = MutationAuthority.Granted(
        mutationLeaseId = WIDEST_UUID,
        mutationLeaseGeneration = Long.MAX_VALUE,
        leaseRemainingAtPhoneSendMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
    )
}
