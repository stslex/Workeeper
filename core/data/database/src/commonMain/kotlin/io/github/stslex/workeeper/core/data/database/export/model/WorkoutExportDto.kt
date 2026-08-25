// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export.model

import kotlinx.serialization.Serializable

/**
 * Root envelope of the AI-readable snapshot: a nested projection of the workout graph,
 * with UTC ISO-8601 timestamps. See documentation/feature-specs/drive-ai-export.md.
 */
@Serializable
internal data class WorkoutExportDto(
    val schemaVersion: Int,
    val exportedAt: String,
    val source: SourceExportDto,
    val exercises: List<ExerciseExportDto>,
    val trainings: List<TrainingExportDto>,
)

@Serializable
internal data class SourceExportDto(
    val appVersion: String,
    val dbSchemaVersion: Int,
    val deviceModel: String? = null,
)

@Serializable
internal data class ExerciseExportDto(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeExportDto,
    val description: String? = null,
    val isAdhoc: Boolean,
    val archived: Boolean,
    val createdAt: String,
    val archivedAt: String? = null,
    val tags: List<String>,
    val lastAdhocSets: List<PlanSetExportDto>? = null,
)

@Serializable
internal data class TrainingExportDto(
    val uuid: String,
    val name: String,
    val description: String? = null,
    val isAdhoc: Boolean,
    val archived: Boolean,
    val createdAt: String,
    val archivedAt: String? = null,
    val tags: List<String>,
    val plan: List<PlanExerciseExportDto>,
    val sessions: List<SessionExportDto>,
)

@Serializable
internal data class PlanExerciseExportDto(
    val exerciseUuid: String,
    val exerciseName: String,
    val position: Int,
    val planSets: List<PlanSetExportDto>,
)

@Serializable
internal data class PlanSetExportDto(
    val reps: Int,
    val weight: Double? = null,
    val type: SetTypeExportDto,
)

@Serializable
internal data class SessionExportDto(
    val uuid: String,
    val state: SessionStateExportDto,
    val startedAt: String,
    val finishedAt: String? = null,
    val performedExercises: List<PerformedExerciseExportDto>,
)

@Serializable
internal data class PerformedExerciseExportDto(
    val exerciseUuid: String,
    val exerciseName: String,
    val position: Int,
    val skipped: Boolean,
    val sets: List<SetExportDto>,
)

@Serializable
internal data class SetExportDto(
    val position: Int,
    val reps: Int,
    val weight: Double? = null,
    val type: SetTypeExportDto,
)

@Serializable
internal enum class ExerciseTypeExportDto { WEIGHTED, WEIGHTLESS }

@Serializable
internal enum class SessionStateExportDto { IN_PROGRESS, FINISHED }

/** The export's single set-type vocabulary; plan and performed sets both normalize into it. */
@Serializable
internal enum class SetTypeExportDto { WARM, WORK, FAIL, DROP }
