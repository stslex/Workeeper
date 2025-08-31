package io.github.stslex.workeeper.core.database.exercise

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.util.UUID

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises_table ORDER BY timestamp DESC")
    fun getAll(): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercises_table WHERE uuid = :uuid LIMIT 1")
    suspend fun getExercise(uuid: UUID): ExerciseEntity?

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(exercise: List<ExerciseEntity>)

    @Query("DELETE FROM exercises_table")
    suspend fun clear()
}