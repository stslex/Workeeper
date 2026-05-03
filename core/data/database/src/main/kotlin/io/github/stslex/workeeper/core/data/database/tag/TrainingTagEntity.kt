package io.github.stslex.workeeper.core.database.tag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import kotlin.uuid.Uuid

@Entity(
    tableName = "training_tag_table",
    primaryKeys = ["training_uuid", "tag_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["training_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["tag_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_uuid", "training_uuid"])],
)
data class TrainingTagEntity(
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "tag_uuid") val tagUuid: Uuid,
)
