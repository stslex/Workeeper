// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.Screen
import org.junit.jupiter.api.Assertions.assertEquals
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

    private companion object {
        const val SENTINEL_URI = "content://sentinel/Sentinel-Holiday-7f3a.jpg"
    }
}
