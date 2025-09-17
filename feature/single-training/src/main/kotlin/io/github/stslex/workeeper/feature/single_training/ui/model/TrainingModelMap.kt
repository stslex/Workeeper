package io.github.stslex.workeeper.feature.single_training.ui.model

import io.github.stslex.workeeper.core.core.result.Mapping
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingModelMap : Mapping<TrainingDataModel, TrainingUiModel> {

    override fun invoke(data: TrainingDataModel): TrainingUiModel = TrainingUiModel(
        uuid = data.uuid,
        name = data.name,
        labels = data.labels.toImmutableList(),
        exerciseUuids = data.exerciseUuids.toImmutableList(),
        date = DateProperty.new(data.timestamp)
    )
}