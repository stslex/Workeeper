package io.github.stslex.workeeper.feature.exercise.ui.mvi.mappers

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.toData
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import javax.inject.Inject

@ViewModelScoped
class ExerciseUiMap @Inject constructor() : Mapper<State, ExerciseChangeDataModel> {

    override fun invoke(data: State): ExerciseChangeDataModel = ExerciseChangeDataModel(
        uuid = data.uuid,
        name = data.name.value,
        sets = data.sets.map { it.toData() },
        timestamp = data.dateProperty.value,
        trainingUuid = data.trainingUuid,
        labels = data.labels,
    )
}
