package io.github.stslex.workeeper.core.database.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "exercise_table",
    indices = [
        Index(value = ["archived", "name"]),
        Index(value = ["archived"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "type")
    val type: ExerciseTypeEntity,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
