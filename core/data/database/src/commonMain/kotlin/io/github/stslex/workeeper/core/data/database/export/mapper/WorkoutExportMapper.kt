// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export.mapper

import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.export.model.ExerciseExportDto
import io.github.stslex.workeeper.core.data.database.export.model.ExerciseTypeExportDto
import io.github.stslex.workeeper.core.data.database.export.model.PerformedExerciseExportDto
import io.github.stslex.workeeper.core.data.database.export.model.PlanExerciseExportDto
import io.github.stslex.workeeper.core.data.database.export.model.PlanSetExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SessionExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SessionStateExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SetExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SetTypeExportDto
import io.github.stslex.workeeper.core.data.database.export.model.TrainingExportDto
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlin.time.Instant

/** Pure entity → export-DTO conversions for the AI snapshot; children are passed in. */
internal object WorkoutExportMapper {

    /** DB epoch-millis → UTC ISO-8601 (e.g. `2026-06-26T10:42:23Z`). */
    fun epochToIso(epochMs: Long): String = Instant.fromEpochMilliseconds(epochMs).toString()

    fun exercise(entity: ExerciseEntity, tags: List<String>): ExerciseExportDto = ExerciseExportDto(
        uuid = entity.uuid.toString(),
        name = entity.name,
        type = entity.type.toExport(),
        description = entity.description,
        isAdhoc = entity.isAdhoc,
        archived = entity.archived,
        createdAt = epochToIso(entity.createdAt),
        archivedAt = entity.archivedAt?.let(::epochToIso),
        tags = tags,
        // imagePath intentionally omitted (device-local path, no value to an LLM; spec §7).
        lastAdhocSets = planSets(entity.lastAdhocSets)?.takeIf { it.isNotEmpty() },
    )

    fun training(
        entity: TrainingEntity,
        tags: List<String>,
        plan: List<PlanExerciseExportDto>,
        sessions: List<SessionExportDto>,
    ): TrainingExportDto = TrainingExportDto(
        uuid = entity.uuid.toString(),
        name = entity.name,
        description = entity.description,
        isAdhoc = entity.isAdhoc,
        archived = entity.archived,
        createdAt = epochToIso(entity.createdAt),
        archivedAt = entity.archivedAt?.let(::epochToIso),
        tags = tags,
        plan = plan,
        sessions = sessions,
    )

    fun planExercise(entity: TrainingExerciseEntity, exerciseName: String): PlanExerciseExportDto =
        PlanExerciseExportDto(
            exerciseUuid = entity.exerciseUuid.toString(),
            exerciseName = exerciseName,
            position = entity.position,
            planSets = planSets(entity.planSets).orEmpty(),
        )

    fun session(
        entity: SessionEntity,
        performedExercises: List<PerformedExerciseExportDto>,
    ): SessionExportDto = SessionExportDto(
        uuid = entity.uuid.toString(),
        state = entity.state.toExport(),
        startedAt = epochToIso(entity.startedAt),
        finishedAt = entity.finishedAt?.let(::epochToIso),
        performedExercises = performedExercises,
    )

    fun performed(
        entity: PerformedExerciseEntity,
        exerciseName: String,
        sets: List<SetEntity>,
    ): PerformedExerciseExportDto = PerformedExerciseExportDto(
        exerciseUuid = entity.exerciseUuid.toString(),
        exerciseName = exerciseName,
        position = entity.position,
        skipped = entity.skipped,
        sets = sets.map(::set),
    )

    fun set(entity: SetEntity): SetExportDto = SetExportDto(
        position = entity.position,
        reps = entity.reps,
        weight = entity.weight,
        type = entity.type.toExport(),
    )

    private fun planSets(json: String?): List<PlanSetExportDto>? =
        PlanSetsConverter.fromJson(json)?.map { it.toExport() }

    private fun PlanSetDataModel.toExport(): PlanSetExportDto = PlanSetExportDto(
        reps = reps,
        weight = weight,
        type = type.toExportSetType(),
    )

    private fun ExerciseTypeEntity.toExport(): ExerciseTypeExportDto = when (this) {
        ExerciseTypeEntity.WEIGHTED -> ExerciseTypeExportDto.WEIGHTED
        ExerciseTypeEntity.WEIGHTLESS -> ExerciseTypeExportDto.WEIGHTLESS
    }

    private fun SessionStateEntity.toExport(): SessionStateExportDto = when (this) {
        SessionStateEntity.IN_PROGRESS -> SessionStateExportDto.IN_PROGRESS
        SessionStateEntity.FINISHED -> SessionStateExportDto.FINISHED
    }

    private fun SetTypeEntity.toExport(): SetTypeExportDto = when (this) {
        SetTypeEntity.WARM -> SetTypeExportDto.WARM
        SetTypeEntity.WORK -> SetTypeExportDto.WORK
        SetTypeEntity.FAIL -> SetTypeExportDto.FAIL
        SetTypeEntity.DROP -> SetTypeExportDto.DROP
    }

    /** Normalizes the stored `WARMUP`/`FAILURE` plan vocabulary onto the performed one. */
    private fun SetTypeDataModel.toExportSetType(): SetTypeExportDto = toEntity().toExport()
}
