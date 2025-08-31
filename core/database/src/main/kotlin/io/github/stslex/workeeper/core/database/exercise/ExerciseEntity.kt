package io.github.stslex.workeeper.core.database.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Entity(tableName = "exercises_table")
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "sets")
    val sets: Int,
    @ColumnInfo(name = "reps")
    val reps: Int,
    @ColumnInfo(name = "weight")
    val weight: Int,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)