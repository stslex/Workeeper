package io.github.stslex.workeeper.core.exercise.session.model

import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import kotlin.uuid.Uuid

data class PerformedExerciseDataModel(
    val uuid: String,
    val sessionUuid: String,
    val exerciseUuid: String,
    val position: Int,
    val skipped: Boolean,
)

internal fun PerformedExerciseEntity.toData(): PerformedExerciseDataModel = PerformedExerciseDataModel(
    uuid = uuid.toString(),
    sessionUuid = sessionUuid.toString(),
    exerciseUuid = exerciseUuid.toString(),
    position = position,
    skipped = skipped,
)

internal fun PerformedExerciseDataModel.toEntity(): PerformedExerciseEntity = PerformedExerciseEntity(
    uuid = Uuid.parse(uuid),
    sessionUuid = Uuid.parse(sessionUuid),
    exerciseUuid = Uuid.parse(exerciseUuid),
    position = position,
    skipped = skipped,
)
