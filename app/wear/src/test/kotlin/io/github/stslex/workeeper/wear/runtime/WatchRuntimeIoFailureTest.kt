// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.ongoing.WriteFailure
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** `Absent(IO_FAILURE)` is a failed read, not empty storage: it must not touch the surviving notification. */
internal class WatchRuntimeIoFailureTest {
    @Test
    fun restartWithAnUnreadableCacheKeepsTheSurvivingNotification() {
        val env = RuntimeTestEnvironment()
        assertTrue(env.accept())
        assertEquals(126_000L, env.notification.existingDeadlineMs(), "Anchor: the accepted snapshot posted a deadline")
        env.trace.clear()
        env.failNextRead = true

        val restarted = assertDoesNotThrow<WatchRuntimeOwner> { env.newOwner() }
        assertTrue(restarted.snapshot.value.recoveryRequired, "The failed read latches recovery")
        assertFalse("cancel" in env.trace, "An IO failure must not cancel the ongoing notification: ${env.trace}")
        assertEquals(126_000L, env.notification.existingDeadlineMs(), "The system deadline survives the failed read")

        val recovered = restarted.onWake()
        assertFalse(recovered.recoveryRequired, "A later successful read clears the latch")
        assertEquals(OngoingStatus.Scheduled(126_000L), recovered.ongoing, "Restore uses min(cache, system)")
    }

    @Test
    fun inProcessRecoveryWithAnUnreadableCacheKeepsTheSurvivingNotification() {
        val env = RuntimeTestEnvironment()
        assertTrue(env.accept())
        env.storage.failure = WriteFailure.AFTER_PUBLISH
        assertThrows(IOException::class.java) { env.owner.disconnected() }
        env.storage.failure = null
        assertTrue(env.owner.snapshot.value.recoveryRequired)
        val surviving = requireNotNull(env.notification.existingDeadlineMs()) {
            "Anchor: the notification survived the failed write"
        }
        env.trace.clear()
        env.failNextRead = true

        assertThrows(IOException::class.java) { env.owner.onWake() }
        assertFalse("cancel" in env.trace, "An IO failure must not cancel the ongoing notification: ${env.trace}")
        assertEquals(surviving, env.notification.existingDeadlineMs(), "The system deadline survives the failed read")

        val recovered = env.owner.onWake()
        assertFalse(recovered.recoveryRequired)
        assertEquals(OngoingStatus.Scheduled(surviving), recovered.ongoing, "Restore uses min(cache, system)")
    }
}
