// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ambient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearAmbientControllerTest {

    @Test
    fun `expiry precedes every published transition and only events read the display clock`() {
        var timestamp = 120_000L
        var clockReads = 0
        val statesAtExpiry = mutableListOf<WearAmbientState>()
        lateinit var controller: WearAmbientController
        controller = WearAmbientController(
            wallClockMillis = {
                clockReads++
                timestamp
            },
            expireAuthority = { statesAtExpiry += controller.state.value },
        )
        val initial = controller.state.value
        assertNull(initial.timestampMillis)
        assertEquals(0, clockReads)

        controller.onEnterAmbient(deviceHasLowBitAmbient = true, burnInProtectionRequired = true)
        val entered = controller.state.value
        assertTrue(entered.isAmbient)
        assertEquals(120_000L, entered.timestampMillis)
        timestamp = 180_000L
        assertEquals(entered, controller.state.value)
        assertEquals(1, clockReads)

        controller.onSystemUpdate()
        val updated = controller.state.value
        assertEquals(180_000L, updated.timestampMillis)
        assertTrue(updated.deviceHasLowBitAmbient)
        assertTrue(updated.burnInProtectionRequired)
        assertNotEquals(entered.offset, updated.offset)

        timestamp = 181_000L
        controller.onExitAmbient()
        assertEquals(listOf(initial, entered, updated), statesAtExpiry)
        assertEquals(3, clockReads)
        assertFalse(controller.state.value.isAmbient)
        assertEquals(181_000L, controller.state.value.timestampMillis)
        assertEquals(WearAmbientOffset(), controller.state.value.offset)
    }

    @Test
    fun `update outside ambient checks expiry without entering ambient or reading the display clock`() {
        var checks = 0
        var clockReads = 0
        val controller = WearAmbientController(
            wallClockMillis = {
                clockReads++
                0L
            },
            expireAuthority = { checks++ },
        )
        controller.onSystemUpdate()
        assertEquals(0, clockReads, "An inactive update must not sample the display clock")
        assertEquals(1, checks)
        assertEquals(WearAmbientState(), controller.state.value)
    }

    @Test
    fun `failed expiry check never exposes an interactive state`() {
        var failExpiry = false
        val controller = WearAmbientController(
            wallClockMillis = { 0L },
            expireAuthority = { check(!failExpiry) },
        )
        controller.onEnterAmbient(deviceHasLowBitAmbient = false, burnInProtectionRequired = false)
        val beforeExit = controller.state.value
        failExpiry = true
        assertThrows(IllegalStateException::class.java) { controller.onExitAmbient() }
        assertEquals(beforeExit, controller.state.value)
    }

    @Test
    fun `wall clock changes cannot supply or extend the monotonic authority deadline`() {
        val deadline = 500L
        var elapsed = 499L
        var wall = 1_000_000L
        var authorized = true
        val controller = WearAmbientController(
            wallClockMillis = { wall },
            expireAuthority = { authorized = authorized && elapsed < deadline },
        )
        controller.onEnterAmbient(deviceHasLowBitAmbient = false, burnInProtectionRequired = false)
        assertTrue(authorized)
        elapsed = deadline
        wall = -1_000_000L
        controller.onSystemUpdate()
        assertFalse(authorized)
        wall = Long.MAX_VALUE
        controller.onExitAmbient()
        assertFalse(authorized)
        assertEquals(Long.MAX_VALUE, controller.state.value.timestampMillis)
    }

    @Test
    fun `burn-in position depends on the event minute and stays within physical pixel bounds`() {
        val positions = (0L until 9L).map { minute ->
            val atMinute = ambientOffset(minute * 60_000L, true)
            assertEquals(atMinute, ambientOffset(minute * 60_000L + 59_999L, true))
            assertTrue(atMinute.xPx in -AMBIENT_MAX_OFFSET_PX..AMBIENT_MAX_OFFSET_PX)
            assertTrue(atMinute.yPx in -AMBIENT_MAX_OFFSET_PX..AMBIENT_MAX_OFFSET_PX)
            assertTrue(atMinute.xPx * atMinute.xPx + atMinute.yPx * atMinute.yPx <= 4)
            atMinute
        }
        assertEquals(9, positions.toSet().size)
        assertEquals(positions.first(), ambientOffset(9L * 60_000L, true))
        assertEquals(positions.last(), ambientOffset(-1L, true))
        listOf(Long.MIN_VALUE, Long.MAX_VALUE).forEach { extreme ->
            val offset = ambientOffset(extreme, true)
            assertTrue(offset.xPx in -AMBIENT_MAX_OFFSET_PX..AMBIENT_MAX_OFFSET_PX)
            assertTrue(offset.yPx in -AMBIENT_MAX_OFFSET_PX..AMBIENT_MAX_OFFSET_PX)
        }
    }

    @Test
    fun `devices without burn-in protection retain a zero offset through every event`() {
        var timestamp = 0L
        val controller = WearAmbientController(wallClockMillis = { timestamp }, expireAuthority = {})
        controller.onEnterAmbient(deviceHasLowBitAmbient = true, burnInProtectionRequired = false)
        repeat(10) {
            timestamp += 60_000L
            controller.onSystemUpdate()
            assertEquals(WearAmbientOffset(), controller.state.value.offset)
        }
        controller.onExitAmbient()
        assertEquals(WearAmbientOffset(), controller.state.value.offset)
    }
}
