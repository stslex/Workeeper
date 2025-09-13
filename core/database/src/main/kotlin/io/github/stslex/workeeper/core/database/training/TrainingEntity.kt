package io.github.stslex.workeeper.core.database.training

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(tableName = "training_table")
data class TrainingEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "uuid")
    val uuid: Uuid,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "exercises")
    val exercises: List<Uuid>,
    @ColumnInfo(name = "labels")
    val labels: List<String>,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)
