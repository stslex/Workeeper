package io.github.stslex.workeeper.feature.single_training.ui.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingChangeMapper : Mapper<TrainingUiModel, TrainingDomainChangeModel> {

    override fun invoke(data: TrainingUiModel): TrainingDomainChangeModel =
        TrainingDomainChangeModel(
            uuid = data.uuid.ifBlank { null },
            name = data.name,
            labels = data.labels,
            exercisesUuids = data.exercises.map { it.uuid },
            timestamp = data.date.value
        )
}