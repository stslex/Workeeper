package io.github.stslex.workeeper.core.database.training

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "training_table",
    indices = [
        Index(value = ["is_adhoc", "archived", "name"]),
        Index(value = ["archived"]),
    ],
)
data class TrainingEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "is_adhoc", defaultValue = "0")
    val isAdhoc: Boolean,
    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long?,
)
