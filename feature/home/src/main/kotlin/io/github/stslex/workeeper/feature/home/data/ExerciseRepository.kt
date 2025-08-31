package io.github.stslex.workeeper.feature.home.data

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.home.data.model.ExerciseDataModel
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>
}

