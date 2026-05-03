package io.github.stslex.workeeper.core.data.exercise.exercise.model

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity

enum class ExerciseTypeDataModel {
    WEIGHTED,
    WEIGHTLESS,
    ;

    fun toEntity(): ExerciseTypeEntity = when (this) {
        WEIGHTED -> ExerciseTypeEntity.WEIGHTED
        WEIGHTLESS -> ExerciseTypeEntity.WEIGHTLESS
    }

    companion object {

        fun ExerciseTypeEntity.toData(): ExerciseTypeDataModel = when (this) {
            ExerciseTypeEntity.WEIGHTED -> WEIGHTED
            ExerciseTypeEntity.WEIGHTLESS -> WEIGHTLESS
        }
    }
}
