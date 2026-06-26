// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.export.mapper.WorkoutExportMapper
import io.github.stslex.workeeper.core.data.database.export.model.SessionExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SourceExportDto
import io.github.stslex.workeeper.core.data.database.export.model.TrainingExportDto
import io.github.stslex.workeeper.core.data.database.export.model.WorkoutExportDto
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

/**
 * Default [DatabaseJsonExporter]: load every table once (the unfiltered `getAll` / batch
 * tag readers), assemble the nested graph in memory with `groupBy` (zero N+1), and encode.
 * `explicitNulls = false` realizes the spec's "omit nullable fields when null"; `prettyPrint`
 * keeps the artifact human-readable.
 */
@Singleton
internal class DatabaseJsonExporterImpl @Inject constructor(
    private val database: AppDatabase,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : DatabaseJsonExporter {

    private val json = Json {
        explicitNulls = false
        prettyPrint = true
    }

    override suspend fun export(
        appVersion: String,
        deviceModel: String?,
        exportedAtEpochMs: Long,
    ): ByteArray = withContext(dispatcher) {
        val exercises = database.exerciseDao.getAll()
        val trainings = database.trainingDao.getAll()
        val index = buildIndex(exercises, trainings)
        val snapshot = WorkoutExportDto(
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = WorkoutExportMapper.epochToIso(exportedAtEpochMs),
            source = SourceExportDto(
                appVersion = appVersion,
                dbSchemaVersion = database.openHelper.readableDatabase.version,
                deviceModel = deviceModel,
            ),
            exercises = exercises.map { entity ->
                WorkoutExportMapper.exercise(entity, index.tagsByExercise[entity.uuid].orEmpty())
            },
            trainings = trainings.map { training -> buildTraining(training, index) },
        )
        json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
    }

    private suspend fun buildIndex(
        exercises: List<ExerciseEntity>,
        trainings: List<TrainingEntity>,
    ): ExportIndex {
        val exerciseUuids = exercises.map { it.uuid }
        val trainingUuids = trainings.map { it.uuid }
        return ExportIndex(
            exerciseNameByUuid = exercises.associate { it.uuid to it.name },
            // Batch readers render `IN ()` for an empty list (SQLite syntax error) — short-circuit.
            tagsByExercise = if (exerciseUuids.isEmpty()) {
                emptyMap()
            } else {
                database.exerciseTagDao.getTagNamesForExercises(exerciseUuids)
                    .groupBy({ it.exerciseUuid }, { it.name })
            },
            tagsByTraining = if (trainingUuids.isEmpty()) {
                emptyMap()
            } else {
                database.trainingTagDao.getTagNamesForTrainings(trainingUuids)
                    .groupBy({ it.trainingUuid }, { it.name })
            },
            planByTraining = database.trainingExerciseDao.getAll().groupBy { it.trainingUuid },
            sessionsByTraining = database.sessionDao.getAll().groupBy { it.trainingUuid },
            performedBySession = database.performedExerciseDao.getAll().groupBy { it.sessionUuid },
            setsByPerformed = database.setDao.getAll().groupBy { it.performedExerciseUuid },
        )
    }

    private fun buildTraining(training: TrainingEntity, index: ExportIndex): TrainingExportDto {
        val plan = index.planByTraining[training.uuid].orEmpty().map { row ->
            WorkoutExportMapper.planExercise(row, index.exerciseNameByUuid[row.exerciseUuid].orEmpty())
        }
        val sessions = index.sessionsByTraining[training.uuid].orEmpty()
            .sortedBy { it.startedAt }
            .map { session -> buildSession(session, index) }
        return WorkoutExportMapper.training(
            entity = training,
            tags = index.tagsByTraining[training.uuid].orEmpty(),
            plan = plan,
            sessions = sessions,
        )
    }

    private fun buildSession(session: SessionEntity, index: ExportIndex): SessionExportDto {
        val performed = index.performedBySession[session.uuid].orEmpty().map { entity ->
            WorkoutExportMapper.performed(
                entity = entity,
                exerciseName = index.exerciseNameByUuid[entity.exerciseUuid].orEmpty(),
                sets = index.setsByPerformed[entity.uuid].orEmpty(),
            )
        }
        return WorkoutExportMapper.session(session, performed)
    }

    /** In-memory join tables for one export pass: every row pre-grouped by its parent uuid. */
    private class ExportIndex(
        val exerciseNameByUuid: Map<Uuid, String>,
        val tagsByExercise: Map<Uuid, List<String>>,
        val tagsByTraining: Map<Uuid, List<String>>,
        val planByTraining: Map<Uuid, List<TrainingExerciseEntity>>,
        val sessionsByTraining: Map<Uuid, List<SessionEntity>>,
        val performedBySession: Map<Uuid, List<PerformedExerciseEntity>>,
        val setsByPerformed: Map<Uuid, List<SetEntity>>,
    )

    private companion object {
        /** Export contract version; independent of `APP_DATABASE_VERSION` (spec D7). */
        const val EXPORT_SCHEMA_VERSION = 1
    }
}
