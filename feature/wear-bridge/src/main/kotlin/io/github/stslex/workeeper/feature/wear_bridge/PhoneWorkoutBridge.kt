// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason

/**
 * The phone authority for the paired watch. The Data Layer listener invokes it through a
 * generation-bound lease (wear-paired-transport.md §6); it answers only while [transportStatus] is empty.
 */
interface PhoneWorkoutBridge {
    val transportStatus: Set<WearPayloadTransportStatus>

    suspend fun getActiveWorkout(
        authenticatedSourceNodeId: String,
        request: GetActiveWorkoutRequest,
    ): ActiveWorkoutSnapshotResponse

    suspend fun completeCurrentSet(
        authenticatedSourceNodeId: String,
        request: CompleteCurrentSetRequest,
    ): CompleteCurrentSetResponse

    suspend fun protocolRejected(
        authenticatedSourceNodeId: String,
        routing: CompleteCommandRouting,
        reason: ProtocolRejectionReason,
    ): CompleteCurrentSetResponse
}

/** Narrow app-graph surface the generation-bound Data Layer listener reads through its lease. */
interface WearBridgeDeps {
    val phoneWorkoutBridge: PhoneWorkoutBridge
}
