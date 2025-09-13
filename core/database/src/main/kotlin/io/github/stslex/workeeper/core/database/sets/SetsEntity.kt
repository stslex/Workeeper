package io.github.stslex.workeeper.core.database.sets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(tableName = "sets_table")
data class SetsEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "uuid")
    val uuid: Uuid,
    @ColumnInfo(name = "exercise_uuid")
    val exerciseUuid: Uuid,
    @ColumnInfo(name = "reps")
    val reps: Int,
    @ColumnInfo(name = "weight")
    val weight: Double,
    @ColumnInfo(name = "type")
    val type: SetsType
)