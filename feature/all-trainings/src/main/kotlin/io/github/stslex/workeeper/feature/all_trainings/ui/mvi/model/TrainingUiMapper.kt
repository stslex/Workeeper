package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import io.github.stslex.workeeper.feature.all_trainings.di.TRAINING_SCOPE_NAME
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class TrainingUiMapper : Mapper<TrainingDataModel, TrainingUiModel> {

    override fun invoke(
        data: TrainingDataModel,
    ): TrainingUiModel = TrainingUiModel(
        uuid = data.uuid,
        name = data.name,
        labels = data.labels.toImmutableList(),
        exerciseUuids = data.exerciseUuids.toImmutableList(),
        date = PropertyHolder.DateProperty().update(data.timestamp),
    )
}
