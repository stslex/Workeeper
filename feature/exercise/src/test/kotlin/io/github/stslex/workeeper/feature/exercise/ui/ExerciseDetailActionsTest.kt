// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExerciseDetailActionsTest {

    /**
     * Regression for the discarded `apply { plus(...) }` result: the permanent-delete
     * overflow item never rendered even when [canPermanentlyDelete] was true. It must be
     * present exactly when permanent delete is allowed, and absent otherwise.
     */
    @Test
    fun `permanent-delete action is appended only when canPermanentlyDelete is true`() {
        val whenDeletable = exerciseDetailActions(canPermanentlyDelete = true) {}
            .map { it.testTag }
        val whenNotDeletable = exerciseDetailActions(canPermanentlyDelete = false) {}
            .map { it.testTag }

        assertEquals(
            listOf(
                "ExerciseDetailEditMenuItem",
                "ExerciseDetailArchiveMenuItem",
                "ExerciseDetailPermanentDeleteMenuItem",
            ),
            whenDeletable,
        )
        assertEquals(
            listOf("ExerciseDetailEditMenuItem", "ExerciseDetailArchiveMenuItem"),
            whenNotDeletable,
        )
    }
}
