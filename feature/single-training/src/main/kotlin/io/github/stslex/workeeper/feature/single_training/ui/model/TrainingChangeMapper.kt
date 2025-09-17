package io.github.stslex.workeeper.feature.single_training.ui.model

import io.github.stslex.workeeper.core.core.result.Mapping
import io.github.stslex.workeeper.core.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingChangeMapper : Mapping<TrainingUiModel, TrainingChangeDataModel> {

    override fun invoke(data: TrainingUiModel): TrainingChangeDataModel = TrainingChangeDataModel(
        uuid = data.uuid.ifBlank { null },
        name = data.name,
        labels = data.labels,
        exerciseUuids = data.exerciseUuids,
        timestamp = data.date.timestamp
    )
}