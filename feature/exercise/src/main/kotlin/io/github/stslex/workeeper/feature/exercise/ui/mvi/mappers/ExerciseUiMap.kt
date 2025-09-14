package io.github.stslex.workeeper.feature.exercise.ui.mvi.mappers

import io.github.stslex.workeeper.core.core.result.Mapping
import io.github.stslex.workeeper.core.exercise.exercise.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.feature.exercise.di.EXERCISE_SCOPE_NAME
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.toData
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = EXERCISE_SCOPE_NAME)
class ExerciseUiMap : Mapping<State, ChangeExerciseDataModel> {

    override fun invoke(data: State): ChangeExerciseDataModel = ChangeExerciseDataModel(
        uuid = data.uuid,
        name = data.name.value,
        sets = data.sets.map { it.toData() },
        timestamp = data.dateProperty.timestamp,
        trainingUuid = data.trainingUuid,
        labels = data.labels,
    )
}