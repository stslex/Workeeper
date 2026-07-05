// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TrainingDetailActionsTest {

    /**
     * Regression for the discarded `apply { plus(...) }` result (twin of the exercise
     * detail bug): the permanent-delete overflow item must appear exactly when
     * [canPermanentlyDelete] is true.
     */
    @Test
    fun `permanent-delete action is appended only when canPermanentlyDelete is true`() {
        val whenDeletable = trainingDetailActions(canPermanentlyDelete = true) {}
            .map { it.testTag }
        val whenNotDeletable = trainingDetailActions(canPermanentlyDelete = false) {}
            .map { it.testTag }

        assertEquals(
            listOf(
                "TrainingDetailMenuButton",
                "TrainingDetailArchiveMenuItem",
                "TrainingDetailPermanentDeleteMenuItem",
            ),
            whenDeletable,
        )
        assertEquals(
            listOf("TrainingDetailMenuButton", "TrainingDetailArchiveMenuItem"),
            whenNotDeletable,
        )
    }
}
