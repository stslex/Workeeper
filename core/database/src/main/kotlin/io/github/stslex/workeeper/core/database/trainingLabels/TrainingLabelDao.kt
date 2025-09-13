package io.github.stslex.workeeper.core.database.trainingLabels

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrainingLabelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(label: TrainingLabelEntity)

    @Query("DELETE FROM training_labels_table WHERE label = :label")
    suspend fun delete(label: String)

    @Query("SELECT * FROM training_labels_table")
    suspend fun getAll(): List<TrainingLabelEntity>

    @Query("DELETE FROM training_labels_table")
    suspend fun clear()
}