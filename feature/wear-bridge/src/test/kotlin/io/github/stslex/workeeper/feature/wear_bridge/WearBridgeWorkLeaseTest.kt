// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearBridgeWorkLeaseTest {

    @Test
    fun `callback returns its value and releases exactly once`() = runTest {
        val holder = RecordingHolder()

        val result = holder.withWearBridgeWorkLease { deps ->
            assertSame(TEST_DEPS, deps)
            "complete"
        }

        assertEquals("complete", result)
        assertEquals(1, holder.lease.releaseCount)
    }

    @Test
    fun `callback failure releases before propagating`() = runTest {
        val holder = RecordingHolder()

        val failure = runCatching {
            holder.withWearBridgeWorkLease<Unit> { error("synthetic callback failure") }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("synthetic callback failure", failure?.message)
        assertEquals(1, holder.lease.releaseCount)
    }

    @Test
    fun `callback timeout and cancellation both release admission`() = runTest {
        val timeoutHolder = RecordingHolder()
        val timeout = runCatching {
            withTimeout(1) {
                timeoutHolder.withWearBridgeWorkLease { awaitCancellation() }
            }
        }.exceptionOrNull()
        assertTrue(timeout is TimeoutCancellationException)
        assertEquals(1, timeoutHolder.lease.releaseCount)

        val cancellationHolder = RecordingHolder()
        val job = backgroundScope.launch {
            cancellationHolder.withWearBridgeWorkLease { awaitCancellation() }
        }
        testScheduler.runCurrent()
        assertFalse(job.isCompleted)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, cancellationHolder.lease.releaseCount)
    }

    @Test
    fun `sealed holder returns null without entering callback`() = runTest {
        var entered = false
        val result = NullHolder.withWearBridgeWorkLease {
            entered = true
        }

        assertEquals(null, result)
        assertFalse(entered)
    }

    private class RecordingHolder : WearBridgeWorkDepsHolder {
        val lease = RecordingLease()
        override suspend fun awaitWearBridgeWorkLease(): WearBridgeWorkLease = lease
    }

    private class RecordingLease : WearBridgeWorkLease {
        override val deps: WearBridgeDeps = TEST_DEPS
        var releaseCount: Int = 0
            private set

        override fun release() {
            releaseCount++
        }
    }

    private data object NullHolder : WearBridgeWorkDepsHolder {
        override suspend fun awaitWearBridgeWorkLease(): WearBridgeWorkLease? = null
    }

    private companion object {
        val TEST_DEPS: WearBridgeDeps = object : WearBridgeDeps {
            override val phoneWorkoutBridge: PhoneWorkoutBridge = object : PhoneWorkoutBridge {
                override val transportStatus: Set<WearPayloadTransportStatus> = emptySet()

                override suspend fun getActiveWorkout(
                    authenticatedSourceNodeId: String,
                    request: GetActiveWorkoutRequest,
                ): ActiveWorkoutSnapshotResponse = error("not used")

                override suspend fun completeCurrentSet(
                    authenticatedSourceNodeId: String,
                    request: CompleteCurrentSetRequest,
                ): CompleteCurrentSetResponse = error("not used")

                override suspend fun protocolRejected(
                    authenticatedSourceNodeId: String,
                    routing: CompleteCommandRouting,
                    reason: ProtocolRejectionReason,
                ): CompleteCurrentSetResponse = error("not used")
            }
        }
    }
}
