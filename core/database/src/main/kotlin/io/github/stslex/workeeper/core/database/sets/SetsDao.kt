package io.github.stslex.workeeper.core.database.sets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlin.uuid.Uuid

@Dao
interface SetsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAll(sets: List<SetsEntity>)

    @Query("DELETE FROM sets_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)

    @Query("SELECT * FROM sets_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun getSets(exerciseUuid: Uuid): List<SetsEntity>

    @Query("DELETE FROM sets_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun deleteSets(exerciseUuid: Uuid)

    @Query("DELETE FROM sets_table")
    suspend fun clear()
}