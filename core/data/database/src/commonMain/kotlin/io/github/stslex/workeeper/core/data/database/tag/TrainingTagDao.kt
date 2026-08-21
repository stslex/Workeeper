package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
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
     * Every (training, tag-name) pair in the database, one [TrainingTagNameRow] per pair
     * (snapshot export); the caller groups by `trainingUuid` in memory. A full-table join
     * with no uuid binding, so it cannot hit `SQLITE_MAX_VARIABLE_NUMBER` for large libraries
     * (API 28-30 ship SQLite < 3.32 where the host-variable limit is 999), and it matches the
     * unfiltered `getAll()` readers the exporter consumes in the same pass.
     */
    @Query(
        """
        SELECT tt.training_uuid AS training_uuid, t.name AS name
        FROM training_tag_table tt
        JOIN tag_table t ON t.uuid = tt.tag_uuid
        ORDER BY tt.training_uuid, t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getAllTrainingTagNames(): List<TrainingTagNameRow>

    @Insert
    suspend fun insert(rows: List<TrainingTagEntity>)

    @Query("DELETE FROM training_tag_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)
}
