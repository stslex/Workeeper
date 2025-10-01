package io.github.stslex.workeeper.core.database.training

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface TrainingDao {

    @Query("SELECT * FROM training_table WHERE name LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getAll(query: String): PagingSource<Int, TrainingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: TrainingEntity)

    @Query("SELECT * FROM training_table WHERE uuid = :uuid")
    suspend fun get(uuid: Uuid): TrainingEntity?

    @Query("SELECT * FROM training_table WHERE uuid = :uuid")
    fun subscribeForTraining(uuid: Uuid): Flow<TrainingEntity?>

    @Query("DELETE FROM training_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Query(
        "SELECT * FROM training_table WHERE name LIKE '%' || :name || '%' AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC",
    )
    suspend fun getTrainings(name: String, startDate: Long, endDate: Long): List<TrainingEntity>

    @Query("DELETE FROM training_table WHERE uuid in (:uuid)")
    suspend fun deleteAll(uuid: List<Uuid>)
}
