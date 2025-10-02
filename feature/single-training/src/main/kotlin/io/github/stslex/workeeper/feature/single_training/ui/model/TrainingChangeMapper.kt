package io.github.stslex.workeeper.feature.single_training.ui.model

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import javax.inject.Inject

@ViewModelScoped
internal class TrainingChangeMapper @Inject constructor() : Mapper<TrainingUiModel, TrainingDomainChangeModel> {

    override fun invoke(data: TrainingUiModel): TrainingDomainChangeModel =
        TrainingDomainChangeModel(
            uuid = data.uuid.ifBlank { null },
            name = data.name,
            labels = data.labels,
            exercisesUuids = data.exercises.map { it.uuid },
            timestamp = data.date.value,
        )
}
