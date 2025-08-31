package io.github.stslex.workeeper.core.database.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "exercises_table")
data class ExerciseEntity constructor(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "uuid")
    val uuid: UUID = UUID.randomUUID(),
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
