package io.github.stslex.workeeper.core.database.trainingLabels

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("training_labels_table")
data class TrainingLabelEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "label")
    val label: String,
)
