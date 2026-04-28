package io.github.stslex.workeeper.core.database.tag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "tag_table",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,
)
