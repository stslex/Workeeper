package io.github.stslex.workeeper.core.database.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(tableName = "exercises_table")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
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
