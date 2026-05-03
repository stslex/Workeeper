// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel

/**
 * One per-exercise plan write that needs to land inside the same transaction as the
 * session-finish state transition. The ad-hoc flag tells the repository which plan
 * column to write — `exercise.last_adhoc_sets` for ad-hoc trainings, the
 * `training_exercise.plan_sets` join row otherwise. Building the list happens in the
 * interactor (read-only computation, can stay outside the txn) and is then handed to
 * `SessionRepository.finishSessionAtomic` for the atomic apply.
 */
data class PlanUpdate(
    val trainingUuid: String,
    val exerciseUuid: String,
    val isAdhoc: Boolean,
    val newPlan: List<PlanSetDataModel>?,
)
