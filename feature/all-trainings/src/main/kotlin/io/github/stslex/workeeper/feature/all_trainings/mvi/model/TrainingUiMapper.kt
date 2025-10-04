package io.github.stslex.workeeper.feature.all_trainings.mvi.model

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class TrainingUiMapper @Inject constructor() : Mapper<TrainingDataModel, TrainingUiModel> {

    override fun invoke(
        data: TrainingDataModel,
    ): TrainingUiModel = TrainingUiModel(
        uuid = data.uuid,
        name = data.name,
        labels = data.labels.toImmutableList(),
        exerciseUuids = data.exerciseUuids.toImmutableList(),
        date = PropertyHolder.DateProperty.new(data.timestamp),
    )
}
