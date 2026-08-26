// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.icons

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

/**
 * Which marks flip under an RTL layout direction, asserted directly - `autoMirror` is a property
 * of the vector, so LTR goldens cannot see it. The non-directional half is the negative control.
 */
internal class AppIconsMirroringTest {

    @Test
    fun `directional marks mirror under RTL`() {
        assertAll(
            DIRECTIONAL.map { (name, icon) ->
                Executable {
                    assertTrue(icon.autoMirror, "$name is directional and must set autoMirror")
                }
            },
        )
    }

    @Test
    fun `non-directional marks do not mirror`() {
        assertAll(
            NON_DIRECTIONAL.map { (name, icon) ->
                Executable {
                    assertFalse(icon.autoMirror, "$name carries no direction and must not mirror")
                }
            },
        )
    }

    private companion object {

        /** "Back", "forward", "onward" — the marks whose meaning is a direction. */
        val DIRECTIONAL: List<Pair<String, ImageVector>> = listOf(
            "ChevronLeft" to AppIcons.ChevronLeft,
            "ChevronRight" to AppIcons.ChevronRight,
        )

        /** Everything whose meaning survives a mirror, [AppIcons.Skip] included. */
        val NON_DIRECTIONAL: List<Pair<String, ImageVector>> = listOf(
            "ChevronDown" to AppIcons.ChevronDown,
            "Skip" to AppIcons.Skip,
            "Close" to AppIcons.Close,
            "Check" to AppIcons.Check,
            "Trash" to AppIcons.Trash,
            "MoreVertical" to AppIcons.MoreVertical,
            "Plus" to AppIcons.Plus,
            "Archive" to AppIcons.Archive,
            "Home" to AppIcons.Home,
            "ExerciseWeighted" to AppIcons.ExerciseWeighted,
            "ExerciseWeightless" to AppIcons.ExerciseWeightless,
        )
    }
}
