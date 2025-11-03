package io.github.stslex.workeeper.core.exercise.labels.model

import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelEntity

data class LabelDataModel(
    val label: String,
)

internal fun TrainingLabelEntity.toData(): LabelDataModel = LabelDataModel(label)

internal fun LabelDataModel.toEntity(): TrainingLabelEntity = TrainingLabelEntity(label)
