package io.github.stslex.workeeper.core.data.database.session

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlin.uuid.Uuid

@Entity(
    tableName = "session_table",
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["training_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["training_uuid", "finished_at"]),
        Index(value = ["state"]),
        Index(value = ["finished_at"]),
    ],
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "training_uuid")
    val trainingUuid: Uuid,
    @ColumnInfo(name = "state")
    val state: SessionStateEntity,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
)
