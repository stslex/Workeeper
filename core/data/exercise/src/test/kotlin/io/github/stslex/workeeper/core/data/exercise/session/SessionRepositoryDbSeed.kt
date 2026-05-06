// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlin.uuid.Uuid

internal suspend fun RepositoryTestEnv.seedTraining(
    uuid: Uuid = Uuid.random(),
    name: String = "Push Day",
    isAdhoc: Boolean = false,
    archived: Boolean = false,
    createdAt: Long = 0L,
): TrainingEntity = TrainingEntity(
    uuid = uuid,
    name = name,
    description = null,
    isAdhoc = isAdhoc,
    archived = archived,
    createdAt = createdAt,
    archivedAt = null,
).also { trainingDao.insert(it) }

internal suspend fun RepositoryTestEnv.seedExercise(
    uuid: Uuid = Uuid.random(),
    name: String = "Bench-${Uuid.random()}",
    type: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
    isAdhoc: Boolean = false,
    lastAdhocSets: String? = null,
): ExerciseEntity = ExerciseEntity(
    uuid = uuid,
    name = name,
    type = type,
    description = null,
    imagePath = null,
    archived = false,
    createdAt = 0L,
    archivedAt = null,
    lastAdhocSets = lastAdhocSets,
    isAdhoc = isAdhoc,
).also { exerciseDao.insert(it) }

internal suspend fun RepositoryTestEnv.seedSession(
    trainingUuid: Uuid,
    uuid: Uuid = Uuid.random(),
    state: SessionStateEntity = SessionStateEntity.IN_PROGRESS,
    startedAt: Long = 1_000L,
    finishedAt: Long? = null,
): SessionEntity = SessionEntity(
    uuid = uuid,
    trainingUuid = trainingUuid,
    state = state,
    startedAt = startedAt,
    finishedAt = finishedAt,
).also { sessionDao.insert(it) }

internal suspend fun RepositoryTestEnv.seedPerformed(
    sessionUuid: Uuid,
    exerciseUuid: Uuid,
    position: Int = 0,
    skipped: Boolean = false,
    uuid: Uuid = Uuid.random(),
): PerformedExerciseEntity = PerformedExerciseEntity(
    uuid = uuid,
    sessionUuid = sessionUuid,
    exerciseUuid = exerciseUuid,
    position = position,
    skipped = skipped,
).also { performedExerciseDao.insert(listOf(it)) }

internal suspend fun RepositoryTestEnv.seedSet(
    performedExerciseUuid: Uuid,
    position: Int = 0,
    weight: Double? = 100.0,
    reps: Int = 5,
    type: SetTypeEntity = SetTypeEntity.WORK,
    uuid: Uuid = Uuid.random(),
): SetEntity = SetEntity(
    uuid = uuid,
    performedExerciseUuid = performedExerciseUuid,
    position = position,
    reps = reps,
    weight = weight,
    type = type,
).also { setDao.insert(it) }

internal suspend fun RepositoryTestEnv.seedTrainingExercise(
    trainingUuid: Uuid,
    exerciseUuid: Uuid,
    position: Int = 0,
    planSets: String? = null,
): TrainingExerciseEntity = TrainingExerciseEntity(
    trainingUuid = trainingUuid,
    exerciseUuid = exerciseUuid,
    position = position,
    planSets = planSets,
).also { trainingExerciseDao.insert(listOf(it)) }
