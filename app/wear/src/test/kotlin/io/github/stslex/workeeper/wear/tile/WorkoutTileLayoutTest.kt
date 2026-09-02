// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkoutTileLayoutTest {

    @Test
    fun `whole tile is exactly one 48dp launch target with no mutation action`() {
        val layout = WorkoutTileLayout.build(
            packageName = "io.github.stslex.workeeper.dev",
            activityClassName = "io.github.stslex.workeeper.wear.MainActivity",
            lines = listOf("Training", "Exercise", "Set 1 of 3"),
        )
        val root = layout.root as LayoutElementBuilders.Box
        val clickable = requireNotNull(requireNotNull(root.modifiers).clickable)
        assertEquals(WorkoutTileLayout.LAUNCH_CLICK_ID, clickable.id)
        assertEquals(48f, clickable.minimumClickableWidth.value)
        assertEquals(48f, clickable.minimumClickableHeight.value)
        val launch = clickable.onClick as ActionBuilders.LaunchAction
        assertEquals(
            "io.github.stslex.workeeper.dev",
            requireNotNull(launch.androidActivity).packageName,
        )
        assertEquals(1, countClickables(root))
        assertTrue(root.contents.single() is LayoutElementBuilders.Column)
        assertTrue(WorkoutTileLayout.LAUNCH_CLICK_ID !in setOf("complete", "increment", "decrement"))
    }

    private fun countClickables(element: LayoutElementBuilders.LayoutElement): Int = when (element) {
        is LayoutElementBuilders.Box ->
            (if (element.modifiers?.clickable == null) 0 else 1) +
                element.contents.sumOf(::countClickables)
        is LayoutElementBuilders.Column ->
            (if (element.modifiers?.clickable == null) 0 else 1) +
                element.contents.sumOf(::countClickables)
        else -> 0
    }
}
