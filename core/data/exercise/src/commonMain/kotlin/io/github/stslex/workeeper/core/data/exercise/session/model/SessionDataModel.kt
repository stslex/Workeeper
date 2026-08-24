package io.github.stslex.workeeper.core.data.exercise.session.model

import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionStateDataModel.Companion.toData
import kotlin.uuid.Uuid

data class SessionDataModel(
    val uuid: String,
    val trainingUuid: String,
    val state: SessionStateDataModel,
    val startedAt: Long,
    val finishedAt: Long?,
)

internal fun SessionEntity.toData(): SessionDataModel = SessionDataModel(
    uuid = uuid.toString(),
    trainingUuid = trainingUuid.toString(),
    state = state.toData(),
    startedAt = startedAt,
    finishedAt = finishedAt,
)

internal fun SessionDataModel.toEntity(): SessionEntity = SessionEntity(
    uuid = Uuid.parse(uuid),
    trainingUuid = Uuid.parse(trainingUuid),
    state = state.toEntity(),
    startedAt = startedAt,
    finishedAt = finishedAt,
)
