// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.icons

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

/**
 * Which marks flip under an RTL layout direction, asserted directly.
 *
 * The manifest sets `android:supportsRtl="true"`, so a *directional* glyph built from a fixed path
 * points the wrong way in an RTL locale unless its [ImageVector] carries `autoMirror`. Nothing else
 * in the tree can see this: `autoMirror` is a property of the vector, not a pixel, so the goldens —
 * which render LTR — are byte-identical whichever way it is set, and a semantics test never reads
 * it. §27's standing rule is that anything whose evidence needs more than one static LTR frame owes
 * a direct assertion.
 *
 * **The negative control is half the suite and is the reason it is a gate.** Asserting only that
 * the directional marks mirror would pass just as happily if `strokeIcon` set `autoMirror = true`
 * for every glyph in the file — which would flip the check, the bin and the overflow dots as well,
 * a worse defect than the one being fixed and one this file would not have noticed. So the
 * non-directional marks are asserted to stay put, and the two halves together say *the flag
 * discriminates* rather than *the flag is on*.
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

        /**
         * Everything whose meaning survives a mirror. [AppIcons.Skip] is deliberately here rather
         * than above: it is a media-transport glyph, and a transport timeline reads left-to-right
         * in every locale, so mirroring it would point the control at the wrong end of the track.
         */
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
