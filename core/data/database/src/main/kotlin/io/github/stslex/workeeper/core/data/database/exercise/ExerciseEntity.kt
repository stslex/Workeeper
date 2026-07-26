package io.github.stslex.workeeper.core.data.database.exercise

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "exercise_table",
    indices = [
        Index(value = ["archived", "name"]),
        Index(value = ["archived"]),
        Index(value = ["name"], unique = true),
        Index(value = ["is_adhoc"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
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
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long?,
    @ColumnInfo(name = "last_adhoc_sets")
    val lastAdhocSets: String?,
    @ColumnInfo(name = "is_adhoc", defaultValue = "0")
    val isAdhoc: Boolean = false,
)
