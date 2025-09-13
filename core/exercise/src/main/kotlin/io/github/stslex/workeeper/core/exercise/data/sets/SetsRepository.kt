package io.github.stslex.workeeper.core.exercise.data.sets

import io.github.stslex.workeeper.core.exercise.data.model.SetsChangeDataModel
import io.github.stslex.workeeper.core.exercise.data.model.SetsDataModel

interface SetsRepository {

    suspend fun getSets(exerciseUuid: String): List<SetsDataModel>

    suspend fun addSets(sets: List<SetsChangeDataModel>)
}
