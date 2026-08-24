package io.github.stslex.workeeper.core.data.database.session.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
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
