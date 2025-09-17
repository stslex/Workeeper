package io.github.stslex.workeeper.core.database.training

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlin.uuid.Uuid

@Dao
interface TrainingDao {

    @Query("SELECT * FROM training_table WHERE name LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getAll(query: String): PagingSource<Int, TrainingEntity>

    @Query("SELECT * FROM training_table")
    fun getAll(): List<TrainingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: TrainingEntity)

    @Query("SELECT * FROM training_table WHERE uuid = :uuid")
    suspend fun get(uuid: Uuid): TrainingEntity

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: TrainingEntity)

    @Query("DELETE FROM training_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Query("DELETE FROM training_table WHERE uuid in (:uuid)")
    suspend fun deleteAll(uuid: List<Uuid>)

    @Query("DELETE FROM training_table")
    suspend fun clear()
}