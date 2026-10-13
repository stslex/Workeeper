// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain.model

data class BulkArchiveResult(
    val archivedCount: Int,
    val blocked: List<BlockedExerciseDomain>,
) {

    /**
     * An exercise that stayed active because it is still referenced by at least one
     * active training. [activeTrainings] names those trainings so the UI can tell the
     * user which trainings to detach it from before archiving.
     */
    data class BlockedExerciseDomain(
        val name: String,
        val activeTrainings: List<String>,
    )
}
