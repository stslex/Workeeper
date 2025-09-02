package io.github.stslex.workeeper.core.exercise.data

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>
}

