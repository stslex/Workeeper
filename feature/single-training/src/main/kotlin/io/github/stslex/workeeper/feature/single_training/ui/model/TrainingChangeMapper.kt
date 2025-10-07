package io.github.stslex.workeeper.feature.single_training.ui.model

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomainChangeModel
import javax.inject.Inject

@ViewModelScoped
internal class TrainingChangeMapper @Inject constructor() :
    Mapper<TrainingUiModel, TrainingDomainChangeModel> {

    override fun invoke(data: TrainingUiModel): TrainingDomainChangeModel = with(data) {
        TrainingDomainChangeModel(
            uuid = uuid.ifBlank { null },
            name = name.value,
            labels = labels,
            exercisesUuids = exercises.map { it.uuid },
            timestamp = date.value,
        )
    }
}
