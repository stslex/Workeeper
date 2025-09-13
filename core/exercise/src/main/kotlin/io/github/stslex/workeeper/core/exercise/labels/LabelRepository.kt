package io.github.stslex.workeeper.core.exercise.labels

import io.github.stslex.workeeper.core.exercise.labels.model.LabelDataModel

interface LabelRepository {

    suspend fun getAll(): List<LabelDataModel>

    suspend fun remove(label: String)

    suspend fun add(label: LabelDataModel)
}

