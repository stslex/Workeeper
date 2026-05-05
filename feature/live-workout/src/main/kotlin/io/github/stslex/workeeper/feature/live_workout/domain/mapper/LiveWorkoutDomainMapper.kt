// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.mapper

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionStateDataModel
import io.github.stslex.workeeper.core.data.exercise.sets.PrComparator
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PerformedExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain

@Suppress("TooManyFunctions")
internal object LiveWorkoutDomainMapper {

    fun SessionDataModel.toDomain(): SessionDomain = SessionDomain(
        uuid = uuid,
        trainingUuid = trainingUuid,
        state = state.toDomain(),
        startedAt = startedAt,
        finishedAt = finishedAt,
    )

    fun SessionStateDataModel.toDomain(): SessionStateDomain = when (this) {
        SessionStateDataModel.IN_PROGRESS -> SessionStateDomain.IN_PROGRESS
        SessionStateDataModel.FINISHED -> SessionStateDomain.FINISHED
    }

    fun PerformedExerciseDataModel.toDomain(
        exerciseName: String,
    ): PerformedExerciseDomain = PerformedExerciseDomain(
        uuid = uuid,
        sessionUuid = sessionUuid,
        exerciseUuid = exerciseUuid,
        position = position,
        skipped = skipped,
        exerciseName = exerciseName,
    )

    fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
        ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
        ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
    }

    fun ExerciseTypeDomain.toData(): ExerciseTypeDataModel = when (this) {
        ExerciseTypeDomain.WEIGHTED -> ExerciseTypeDataModel.WEIGHTED
        ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeDataModel.WEIGHTLESS
    }

    fun SetTypeDataModel.toDomain(): SetTypeDomain = when (this) {
        SetTypeDataModel.WARMUP -> SetTypeDomain.WARMUP
        SetTypeDataModel.WORK -> SetTypeDomain.WORK
        SetTypeDataModel.FAILURE -> SetTypeDomain.FAILURE
        SetTypeDataModel.DROP -> SetTypeDomain.DROP
    }

    fun SetTypeDomain.toData(): SetTypeDataModel = when (this) {
        SetTypeDomain.WARMUP -> SetTypeDataModel.WARMUP
        SetTypeDomain.WORK -> SetTypeDataModel.WORK
        SetTypeDomain.FAILURE -> SetTypeDataModel.FAILURE
        SetTypeDomain.DROP -> SetTypeDataModel.DROP
    }

    fun SetsDataType.toDomain(): SetTypeDomain = when (this) {
        SetsDataType.WARM -> SetTypeDomain.WARMUP
        SetsDataType.WORK -> SetTypeDomain.WORK
        SetsDataType.FAIL -> SetTypeDomain.FAILURE
        SetsDataType.DROP -> SetTypeDomain.DROP
    }

    fun SetTypeDomain.toSetsDataType(): SetsDataType = when (this) {
        SetTypeDomain.WARMUP -> SetsDataType.WARM
        SetTypeDomain.WORK -> SetsDataType.WORK
        SetTypeDomain.FAILURE -> SetsDataType.FAIL
        SetTypeDomain.DROP -> SetsDataType.DROP
    }

    fun PlanSetDataModel.toDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    fun PlanSetDomain.toData(): PlanSetDataModel = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = type.toData(),
    )

    fun SetsDataModel.toDomain(): SetDomain = SetDomain(
        uuid = uuid,
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    fun PersonalRecordDataModel.toDomain(): PersonalRecordDomain = PersonalRecordDomain(
        sessionUuid = sessionUuid,
        performedExerciseUuid = performedExerciseUuid,
        setUuid = setUuid,
        weight = weight,
        reps = reps,
        type = type.toDomain(),
        finishedAt = finishedAt,
    )

    fun PlanSetDomain.beatsBaseline(
        baselineWeight: Double?,
        baselineReps: Int?,
        type: ExerciseTypeDomain,
        hasBaseline: Boolean,
    ): Boolean = PrComparator.beats(
        candidate = toData(),
        baselineWeight = baselineWeight,
        baselineReps = baselineReps,
        type = type.toData(),
        hasBaseline = hasBaseline,
    )

    fun bestOfDomain(
        sets: List<PlanSetDomain>,
        type: ExerciseTypeDomain,
    ): PlanSetDomain? = PrComparator.bestOf(
        sets = sets.map { it.toData() },
        type = type.toData(),
    )?.toDomain()
}
