package io.github.stslex.workeeper.feature.single_training.ui.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainModel
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingDomainUiModelMapper(
    private val exerciseMapper: ExerciseDomainUiMapper
) : Mapper<TrainingDomainModel, TrainingUiModel> {

    override operator fun invoke(data: TrainingDomainModel): TrainingUiModel = TrainingUiModel(
        uuid = data.uuid,
        name = data.name,
        labels = data.labels.toImmutableList(),
        exercises = data.exercises
            .map { exerciseMapper(it) }
            .toImmutableList(),
        date = DateProperty.new(data.timestamp)
    )
}