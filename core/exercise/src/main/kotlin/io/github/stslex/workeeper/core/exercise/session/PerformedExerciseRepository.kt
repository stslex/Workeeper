package io.github.stslex.workeeper.core.exercise.session

import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDataModel

interface PerformedExerciseRepository {

    suspend fun getBySession(sessionUuid: String): List<PerformedExerciseDataModel>

    suspend fun insert(rows: List<PerformedExerciseDataModel>)

    suspend fun setSkipped(uuid: String, skipped: Boolean)
}
