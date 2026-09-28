// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WearRuntimeUiEventTest {

    @Test
    fun failedRuntimeEventIsContainedWithoutAutomaticRetry() {
        var calls = 0
        val failure = IOException("Synthetic storage failure")
        val result = assertDoesNotThrow<Result<Unit>> {
            runWearRuntimeUiEvent<Unit> {
                calls++
                throw failure
            }
        }
        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, calls, "A failed event must not automatically retry or issue a handshake")
    }

    @Test
    fun fatalErrorPropagatesWithoutAutomaticRetry() {
        var calls = 0
        val fatal = AssertionError("Synthetic fatal error")
        val thrown = assertThrows(AssertionError::class.java) {
            runWearRuntimeUiEvent<Unit> {
                calls++
                throw fatal
            }
        }
        assertSame(fatal, thrown)
        assertEquals(1, calls, "Fatal errors must propagate without a second event invocation")
    }

    @Test
    fun successfulEventResultIsPreserved() {
        var calls = 0
        val result = runWearRuntimeUiEvent {
            calls++
            false
        }
        assertEquals(false, result.getOrThrow())
        assertEquals(1, calls)
    }
}
