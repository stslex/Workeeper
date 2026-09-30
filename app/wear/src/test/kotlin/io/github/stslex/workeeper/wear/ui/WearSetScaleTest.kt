// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearSetScaleTest {
    @Test
    fun `small totals retain one pill per set and exact current position`() {
        val slots = wearSetScaleSlots(current = 4, total = 8)
        assertEquals((1..8).toList(), slots.map { it.firstSet })
        assertEquals((1..8).toList(), slots.map { it.lastSet })
        assertEquals(listOf(true, true, true, false, false, false, false, false), slots.map { it.completed })
        assertEquals(listOf(false, false, false, true, false, false, false, false), slots.map { it.current })
    }

    @Test
    fun `large totals have at most eight contiguous buckets and one current bucket`() {
        listOf(9, 32, 128, Int.MAX_VALUE).forEach { total ->
            listOf(1, 2, total / 2, total - 1, total).distinct().forEach { current ->
                val slots = wearSetScaleSlots(current, total)
                assertEquals(8, slots.size, "total=$total current=$current")
                assertEquals(1, slots.first().firstSet)
                assertEquals(total, slots.last().lastSet)
                slots.zipWithNext().forEach { (before, after) ->
                    assertEquals(before.lastSet.toLong() + 1L, after.firstSet.toLong())
                }
                assertEquals(total.toLong(), slots.sumOf { it.lastSet.toLong() - it.firstSet + 1L })
                assertEquals(1, slots.count { it.current })
                slots.forEach { slot ->
                    assertTrue(slot.firstSet in 1..slot.lastSet)
                    assertEquals(current in slot.firstSet..slot.lastSet, slot.current)
                    assertEquals(slot.lastSet < current, slot.completed)
                    assertFalse(slot.current && slot.completed)
                }
            }
        }
    }

    @Test
    fun `one set is current and never marked completed`() {
        assertEquals(listOf(WearSetScaleSlot(1, 1, completed = false, current = true)), wearSetScaleSlots(1, 1))
    }

    @Test
    fun `invalid progress is rejected rather than producing empty or negative slots`() {
        listOf(0 to 1, 1 to 0, 2 to 1, -1 to 8).forEach { (current, total) ->
            assertThrows(IllegalArgumentException::class.java) { wearSetScaleSlots(current, total) }
        }
    }
}
