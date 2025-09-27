package io.github.stslex.workeeper.feature.single_training.ui.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.single_training.di.TRAINING_SCOPE_NAME
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseDomainModel
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = TRAINING_SCOPE_NAME)
internal class ExerciseDomainUiMapper : Mapper<ExerciseDomainModel, ExerciseUiModel> {

    override fun invoke(data: ExerciseDomainModel): ExerciseUiModel = ExerciseUiModel(
        uuid = data.uuid,
        labels = data.labels.toImmutableList(),
        sets = data.sets,
        name = data.name,
        timestamp = PropertyHolder.DateProperty.new(data.timestamp),
    )
}
