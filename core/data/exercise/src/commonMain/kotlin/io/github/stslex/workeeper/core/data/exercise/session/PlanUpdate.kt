// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel

/**
 * One per-exercise plan write applied inside the session-finish transaction; [isAdhoc] picks the
 * column (`exercise.last_adhoc_sets` vs the `training_exercise.plan_sets` join row).
 */
data class PlanUpdate(
    val trainingUuid: String,
    val exerciseUuid: String,
    val isAdhoc: Boolean,
    val newPlan: List<PlanSetDataModel>?,
)
