package io.github.stslex.workeeper.core.data.database.tag

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

    /**
     * Denormalized tag names for many trainings in one query (snapshot export).
     * Returns one [TrainingTagNameRow] per (training, tag) pair; the caller groups
     * by `trainingUuid` in memory. Mirrors the `getPlanSetsBatch` batch convention;
     * callers must short-circuit an empty [trainingUuids] list (Room renders `IN ()`).
     */
    @Query(
        """
        SELECT tt.training_uuid AS training_uuid, t.name AS name
        FROM training_tag_table tt
        JOIN tag_table t ON t.uuid = tt.tag_uuid
        WHERE tt.training_uuid IN (:trainingUuids)
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getTagNamesForTrainings(trainingUuids: List<Uuid>): List<TrainingTagNameRow>

    @Insert
    suspend fun insert(rows: List<TrainingTagEntity>)

    @Query("DELETE FROM training_tag_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)
}
