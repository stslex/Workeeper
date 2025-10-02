package io.github.stslex.workeeper.feature.single_training.ui.model

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class TrainingDomainUiModelMapper @Inject constructor(
    private val exerciseMapper: ExerciseDomainUiMapper,
) : Mapper<TrainingDomainModel, TrainingUiModel> {

    override operator fun invoke(data: TrainingDomainModel): TrainingUiModel = TrainingUiModel(
        uuid = data.uuid,
        name = data.name,
        labels = data.labels.toImmutableList(),
        exercises = data.exercises
            .map { exerciseMapper(it) }
            .toImmutableList(),
        date = PropertyHolder.DateProperty.new(data.timestamp),
    )
}
