// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export

import androidx.room.withTransaction
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Default [DatabaseJsonExporter]: load every table once (the unfiltered `getAll` / batch
 * tag readers), assemble the nested graph in memory with `groupBy` (zero N+1), and encode.
 * `explicitNulls = false` realizes the spec's "omit nullable fields when null"; `prettyPrint`
 * keeps the artifact human-readable.
 *
 * Metro-owned via `@ContributesBinding(AppScope)` on the (public) impl — derives from the [AppDatabase]
 * `create()` root; `@IODispatcher` is the direct `Dispatchers.IO`. Public for cross-module aggregation (D1).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class DatabaseJsonExporterImpl @Inject constructor(
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
        // Process-stable (only changes on migration at open); read once, outside the transaction.
        val dbSchemaVersion = database.openHelper.readableDatabase.version
        // One consistent snapshot: every table read + the in-memory index build runs inside a
        // single Room transaction. The export is fire-and-forget (spec D2) so it can overlap live
        // workout edits; without the transaction each DAO call would observe its own snapshot and
        // an interleaved insert could surface a child row whose parent/name row was not read (e.g.
        // `exerciseNameByUuid` falling back to ""). The transaction pins all reads to one state.
        val snapshot = database.withTransaction {
            val exercises = async { database.exerciseDao.getAll() }
            val trainings = async { database.trainingDao.getAll() }
            val index = async { buildIndex(exercises) }
            WorkoutExportDto(
                schemaVersion = EXPORT_SCHEMA_VERSION,
                exportedAt = WorkoutExportMapper.epochToIso(exportedAtEpochMs),
                source = SourceExportDto(
                    appVersion = appVersion,
                    dbSchemaVersion = dbSchemaVersion,
                    deviceModel = deviceModel,
                ),
                exercises = exercises.await().map { entity ->
                    WorkoutExportMapper.exercise(
                        entity,
                        index.await().tagsByExercise[entity.uuid].orEmpty(),
                    )
                },
                trainings = trainings.await()
                    .map { training -> buildTraining(training, index.await()) },
            )
        }
        // Encode outside the transaction — pure CPU, no DB access; keeps the transaction short.
        json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
    }

    private suspend fun CoroutineScope.buildIndex(exercises: Deferred<List<ExerciseEntity>>): ExportIndex {
        val tagsByExerciseDeferred = async {
            database.exerciseTagDao.getAllExerciseTagNames()
                .groupBy({ it.exerciseUuid }, { it.name })
        }
        val tagsByTrainingDeferred = async {
            database.trainingTagDao.getAllTrainingTagNames()
                .groupBy({ it.trainingUuid }, { it.name })
        }
        val planByTrainingDeferred = async {
            database.trainingExerciseDao.getAll().groupBy { it.trainingUuid }
        }
        val sessionsByTrainingDeferred = async {
            database.sessionDao.getAll().groupBy { it.trainingUuid }
        }
        val performedBySessionDeferred = async {
            database.performedExerciseDao.getAll().groupBy { it.sessionUuid }
        }
        val setsByPerformedDeferred = async {
            database.setDao.getAll().groupBy { it.performedExerciseUuid }
        }
        val exerciseNameByUuidDeferred = async {
            exercises.await().associate { it.uuid to it.name }
        }
        return ExportIndex(
            exerciseNameByUuid = exerciseNameByUuidDeferred.await(),
            // Full-table tag joins (no uuid binding) — cannot hit SQLITE_MAX_VARIABLE_NUMBER, and
            // consistent with the unfiltered getAll() readers below.
            tagsByExercise = tagsByExerciseDeferred.await(),
            tagsByTraining = tagsByTrainingDeferred.await(),
            planByTraining = planByTrainingDeferred.await(),
            sessionsByTraining = sessionsByTrainingDeferred.await(),
            performedBySession = performedBySessionDeferred.await(),
            setsByPerformed = setsByPerformedDeferred.await(),
        )
    }

    private fun buildTraining(training: TrainingEntity, index: ExportIndex): TrainingExportDto {
        val plan = index.planByTraining[training.uuid].orEmpty().map { row ->
            WorkoutExportMapper.planExercise(
                row,
                index.exerciseNameByUuid[row.exerciseUuid].orEmpty(),
            )
        }
        val sessions = index.sessionsByTraining[training.uuid].orEmpty()
            .sortedWith(compareBy({ it.startedAt }, { it.uuid.toString() }))
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
