package io.github.stslex.workeeper.core.database.session.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import kotlin.uuid.Uuid

@Entity(
    tableName = "set_table",
    foreignKeys = [
        ForeignKey(
            entity = PerformedExerciseEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["performed_exercise_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["performed_exercise_uuid", "position"]),
    ],
)
data class SetEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "performed_exercise_uuid")
    val performedExerciseUuid: Uuid,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "reps")
    val reps: Int,
    @ColumnInfo(name = "weight")
    val weight: Double?,
    @ColumnInfo(name = "type")
    val type: SetTypeEntity,
)
