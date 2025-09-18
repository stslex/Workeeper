package io.github.stslex.workeeper.core.database.exercise

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises_table ORDER BY timestamp DESC")
    fun getAll(): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercises_table WHERE name LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getAll(query: String): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercises_table WHERE uuid = :uuid")
    suspend fun getExercise(uuid: Uuid): ExerciseEntity?

    @Query("SELECT * FROM exercises_table WHERE name LIKE '%' || :name || '%' AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getExercises(
        name: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises_table WHERE name = :name AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getExercisesExactly(
        name: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<ExerciseEntity>>

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(exercise: List<ExerciseEntity>)

    @Query("DELETE FROM exercises_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Query("DELETE FROM exercises_table WHERE uuid IN (:uuid)")
    suspend fun delete(uuid: List<Uuid>)

    @Query("DELETE FROM exercises_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteAllByTraining(trainingUuid: Uuid)

    @Query("DELETE FROM exercises_table WHERE training_uuid in (:trainingUuid)")
    suspend fun deleteAllByTrainings(trainingUuid: List<Uuid>)

    @Query("SELECT * FROM exercises_table WHERE name LIKE '%' || :query || '%' GROUP BY name ORDER BY MAX(timestamp) DESC LIMIT 10")
    suspend fun searchUnique(query: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercises_table WHERE name LIKE '%' || :query || '%' AND name != :query GROUP BY name ORDER BY MAX(timestamp) DESC LIMIT 10")
    suspend fun searchUniqueExclude(query: String): List<ExerciseEntity>

    @Query("DELETE FROM exercises_table")
    suspend fun clear()
}