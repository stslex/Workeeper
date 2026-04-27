package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    fun getUniqueExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    suspend fun getExercisesByUuid(uuids: List<String>): List<ExerciseDataModel>

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun getExercises(name: String, startDate: Long, endDate: Long): List<ExerciseDataModel>

    suspend fun saveItem(item: ExerciseChangeDataModel)

    suspend fun deleteItem(uuid: String)

    suspend fun searchItemsWithExclude(query: String): List<ExerciseDataModel>

    suspend fun deleteAllItems(uuids: List<Uuid>)

    suspend fun archive(uuid: String)

    suspend fun restore(uuid: String)

    suspend fun permanentDelete(uuid: String)

    suspend fun canArchive(uuid: String): Boolean

    suspend fun canPermanentlyDeleteImmediately(uuid: String): Boolean

    suspend fun getActiveTrainingsUsing(exerciseUuid: String): List<String>

    fun pagedArchived(): Flow<PagingData<ExerciseDataModel>>

    fun observeArchivedCount(): Flow<Int>

    suspend fun countSessionsUsing(exerciseUuid: String): Int

    fun pagedActiveByTags(tagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>

    suspend fun getRecentHistory(exerciseUuid: String, limit: Int): List<HistoryEntry>
}
