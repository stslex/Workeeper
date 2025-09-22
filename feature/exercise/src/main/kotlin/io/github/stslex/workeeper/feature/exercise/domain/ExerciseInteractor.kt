package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel

interface ExerciseInteractor {

    suspend fun saveItem(item: ExerciseChangeDataModel)
}
