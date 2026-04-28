package io.github.stslex.workeeper.core.database.tag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlin.uuid.Uuid

@Dao
interface TrainingTagDao {

    @Query("SELECT tag_uuid FROM training_tag_table WHERE training_uuid = :trainingUuid")
    suspend fun getTagUuids(trainingUuid: Uuid): List<Uuid>

    @Query(
        """
        SELECT t.name FROM tag_table t
        JOIN training_tag_table tt ON tt.tag_uuid = t.uuid
        WHERE tt.training_uuid = :trainingUuid
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getTagNames(trainingUuid: Uuid): List<String>

    @Insert
    suspend fun insert(rows: List<TrainingTagEntity>)

    @Query("DELETE FROM training_tag_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)
}
