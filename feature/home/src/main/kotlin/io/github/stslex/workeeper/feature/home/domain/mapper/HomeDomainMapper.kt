// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain

internal fun SessionRepository.ActiveSessionWithStats.toDomain(): ActiveSessionWithStatsDomain =
    ActiveSessionWithStatsDomain(
        sessionUuid = sessionUuid,
        trainingUuid = trainingUuid,
        trainingName = trainingName,
        isAdhoc = isAdhoc,
        startedAt = startedAt,
        totalCount = totalCount,
        doneCount = doneCount,
    )

internal fun RecentSessionDataModel.toDomain(): RecentSessionDomain = RecentSessionDomain(
    sessionUuid = sessionUuid,
    trainingUuid = trainingUuid,
    trainingName = trainingName,
    isAdhoc = isAdhoc,
    startedAt = startedAt,
    finishedAt = finishedAt,
    exerciseCount = exerciseCount,
    setCount = setCount,
)

internal fun TrainingListItem.toDomain(): TrainingListItemDomain = TrainingListItemDomain(
    uuid = data.uuid,
    name = data.name,
    exerciseCount = exerciseCount,
    lastSessionAt = lastSessionAt,
)

internal fun ActiveSessionInfo.toDomain(): ActiveSessionDomain = ActiveSessionDomain(
    sessionUuid = sessionUuid,
    trainingUuid = trainingUuid,
    startedAt = startedAt,
)

internal fun SessionConflictResolver.Resolution.toDomain(): StartSessionConflict = when (this) {
    SessionConflictResolver.Resolution.ProceedFresh -> StartSessionConflict.ProceedFresh
    is SessionConflictResolver.Resolution.SilentResume -> StartSessionConflict.SilentResume(sessionUuid)
    is SessionConflictResolver.Resolution.NeedsUserChoice -> StartSessionConflict.NeedsUserChoice(active.toDomain())
}
