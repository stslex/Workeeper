package io.github.stslex.workeeper.core.database.exercise

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises_table ORDER BY timestamp DESC")
    fun getAll(): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercises_table WHERE name LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getAll(query: String): PagingSource<Int, ExerciseEntity>

    @Query(
        """
        SELECT e1.* FROM exercises_table e1
        INNER JOIN (
            SELECT name, MAX(timestamp) as max_timestamp
            FROM exercises_table
            WHERE name LIKE '%' || :query || '%'
            GROUP BY name
        ) e2 ON e1.name = e2.name AND e1.timestamp = e2.max_timestamp
        ORDER BY e1.timestamp DESC
    """,
    )
    fun getAllUnique(query: String): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercises_table WHERE uuid = :uuid")
    suspend fun getExercise(uuid: Uuid): ExerciseEntity?

    @Query("SELECT * FROM exercises_table WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<Uuid>): List<ExerciseEntity>?

    @Query("SELECT * FROM exercises_table WHERE name = :name ORDER BY timestamp DESC LIMIT 1")
    suspend fun getExerciseByName(name: String): ExerciseEntity?

    @Query(
        """
            SELECT * FROM exercises_table 
            WHERE name LIKE '%' || :name || '%' 
            AND timestamp BETWEEN :startDate AND :endDate 
            ORDER BY timestamp DESC
            """,
    )
    suspend fun getExercises(name: String, startDate: Long, endDate: Long): List<ExerciseEntity>

    @Query(
        """
            SELECT * FROM exercises_table 
            WHERE name = :name 
            AND timestamp BETWEEN :startDate AND :endDate 
            ORDER BY timestamp DESC
            """,
    )
    fun getExercisesExactly(
        name: String,
        startDate: Long,
        endDate: Long,
    ): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Query("DELETE FROM exercises_table WHERE uuid IN (:uuid)")
    suspend fun delete(uuid: List<Uuid>)

    @Query("DELETE FROM exercises_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteAllByTraining(trainingUuid: Uuid)

    @Query("DELETE FROM exercises_table WHERE training_uuid in (:trainingUuid)")
    suspend fun deleteAllByTrainings(trainingUuid: List<Uuid>)

    @Query(
        """
        SELECT e1.* FROM exercises_table e1
        INNER JOIN (
            SELECT name, MAX(timestamp) as max_timestamp
            FROM exercises_table
            WHERE name LIKE '%' || :query || '%'
            AND name != :query
            GROUP BY name
            ORDER BY max_timestamp DESC
            LIMIT 10
        ) e2 ON e1.name = e2.name AND e1.timestamp = e2.max_timestamp
        ORDER BY e1.timestamp DESC
    """,
    )
    suspend fun searchUniqueExclude(query: String): List<ExerciseEntity>
}
