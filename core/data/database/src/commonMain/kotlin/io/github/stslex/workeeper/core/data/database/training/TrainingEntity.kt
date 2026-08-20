package io.github.stslex.workeeper.core.data.database.training

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
