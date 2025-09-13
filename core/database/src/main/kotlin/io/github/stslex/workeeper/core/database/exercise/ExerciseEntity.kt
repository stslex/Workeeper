package io.github.stslex.workeeper.core.database.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import kotlin.uuid.Uuid

@Entity(tableName = "exercises_table")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "training_uuid")
    val trainingUuid: Uuid?,
    @ColumnInfo(name = "labels")
    val labels: List<String>,
    @ColumnInfo(name = "sets")
    val sets: List<SetsEntity>,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)
