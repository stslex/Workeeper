package io.github.stslex.workeeper.core.database.session

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

@Entity(
    tableName = "performed_exercise_table",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["session_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["exercise_uuid"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["session_uuid", "position"]),
        Index(value = ["exercise_uuid"]),
    ],
)
data class PerformedExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "session_uuid")
    val sessionUuid: Uuid,
    @ColumnInfo(name = "exercise_uuid")
    val exerciseUuid: Uuid,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "skipped", defaultValue = "0")
    val skipped: Boolean,
)
