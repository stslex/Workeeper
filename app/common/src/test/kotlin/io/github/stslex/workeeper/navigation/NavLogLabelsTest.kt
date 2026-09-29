// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** wear-paired-transport.md §9.2: navigation log lines name types, never what a screen carries. */
internal class NavLogLabelsTest {

    @Test
    fun `labels carry type names and never the picked image or a result`() {
        val image = Screen.ExerciseImage(model = SENTINEL_URI, editable = true)

        assertEquals("NavTo(ExerciseImage)", NavCommand.NavTo(image).logLabel())
        assertEquals("ReplaceTo(ExerciseImage)", NavCommand.ReplaceTo(image).logLabel())
        assertEquals("PopBackWithResult(key)", NavCommand.PopBackWithResult("key", SENTINEL_URI).logLabel())
        assertEquals("PopBack", NavCommand.PopBack.logLabel())
        assertEquals("OpenRecovery", NavCommand.OpenRecovery.logLabel())
        assertEquals("ExerciseImage", image.logLabel())
    }

    @Test
    fun `the navigator bus logs labels, never the picked image or a result`() {
        val lines = mutableListOf<String>()
        val logger = mockk<Logger>(relaxed = true)
        every { logger.d(any<() -> String>()) } answers { lines += firstArg<() -> String>()() }
        every { logger.w(any<() -> String>()) } answers { lines += firstArg<() -> String>()() }
        mockkObject(Log)
        try {
            every { Log.tag(any()) } returns logger
            val bus = NavigatorEventBus(mockk(relaxed = true))

            bus.navTo(Screen.ExerciseImage(model = SENTINEL_URI, editable = true))
            bus.replaceTo(Screen.ExerciseImage(model = SENTINEL_URI))
            bus.popBackWithResult(Screen.ExerciseImage::class, SENTINEL_URI)
        } finally {
            unmockkObject(Log)
        }

        // Fragments, not the exact label: whether the path ran is this check's; what the line
        // carries is the leak check's.
        assertTrue(lines.any { "NavTo" in it && "ExerciseImage" in it }, "the navTo path ran: $lines")
        assertTrue(lines.any { "PopBackWithResult(" in it }, "the result path ran: $lines")
        val leaks = lines.filter { "Sentinel" in it }
        assertTrue(leaks.isEmpty(), "a navigation log line carries the picked image: $leaks")
    }

    private companion object {
        const val SENTINEL_URI = "content://sentinel/Sentinel-Holiday-7f3a.jpg"
    }
}
