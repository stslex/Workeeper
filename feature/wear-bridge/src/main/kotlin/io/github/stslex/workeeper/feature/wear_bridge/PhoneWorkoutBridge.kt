// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason

/** Disconnected phone authority. No Data Layer listener invokes it while owner gates are open. */
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

/** Narrow app-graph surface used by a future generation-bound listener after both owner gates. */
interface WearBridgeDeps {
    val phoneWorkoutBridge: PhoneWorkoutBridge
}
