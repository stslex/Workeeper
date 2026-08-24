package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
