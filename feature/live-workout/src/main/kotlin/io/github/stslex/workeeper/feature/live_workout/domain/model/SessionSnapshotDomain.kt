// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

internal data class SessionSnapshotDomain(
    val session: SessionDomain,
    val trainingName: String,
    val isAdhoc: Boolean,
    val exercises: List<LiveExerciseDomain>,
    val preSessionPrSnapshot: Map<String, PersonalRecordDomain?>,
)
