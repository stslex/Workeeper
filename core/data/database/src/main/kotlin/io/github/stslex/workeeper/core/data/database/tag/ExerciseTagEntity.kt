package io.github.stslex.workeeper.core.data.database.tag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

@Entity(
    tableName = "exercise_tag_table",
    primaryKeys = ["exercise_uuid", "tag_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["exercise_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["tag_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_uuid", "exercise_uuid"])],
)
data class ExerciseTagEntity(
    @ColumnInfo(name = "exercise_uuid") val exerciseUuid: Uuid,
    @ColumnInfo(name = "tag_uuid") val tagUuid: Uuid,
)
