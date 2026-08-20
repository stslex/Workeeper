// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export.model

import kotlinx.serialization.Serializable

/**
 * Root envelope of the AI-readable snapshot (spec `drive-ai-export.md` §3). A nested,
 * denormalized projection of the workout graph: a flat [exercises] library plus
 * [trainings], each carrying its plan and session history. Timestamps are UTC
 * ISO-8601 strings; nullable fields are omitted when null (the producer's `Json` sets
 * `explicitNulls = false`).
 *
 * [schemaVersion] is the export contract's own version (independent of the Room
 * `APP_DATABASE_VERSION`); bump it only when this JSON shape changes.
 *
 * All types here are `internal`: the snapshot's public surface is the encoded bytes,
 * not the Kotlin types. They are `@Serializable`, so the repo-wide
 * `proguard/kotlinx-serialization.pro` keeps cover them (incl. the enums).
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

/**
 * Single canonical set-type vocabulary for the export. Performed sets map 1:1 from
 * `SetTypeEntity`; plan sets (stored with the `SetTypeDataModel` `WARMUP`/`FAILURE`
 * vocabulary) are normalized into this one via the existing `SetTypeDataModel.toEntity()`
 * bridge, so the snapshot speaks one set-type language.
 */
@Serializable
internal enum class SetTypeExportDto { WARM, WORK, FAIL, DROP }
