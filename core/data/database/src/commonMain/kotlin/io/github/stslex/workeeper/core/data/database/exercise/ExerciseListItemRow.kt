// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import androidx.room3.ColumnInfo
import androidx.room3.Embedded

/**
 * Projection used by [ExerciseDao.pagedActiveWithStats] and friends. Joins the exercise
 * row with derived stats (session count, linked-training count, last-trained timestamp)
 * so the library tab can render the v2.4 "N sessions · in M trainings · last Xd ago"
 * footer in one paged query.
 */
data class ExerciseListItemRow(
    @Embedded val exercise: ExerciseEntity,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "linked_trainings_count") val linkedTrainingsCount: Int,
    @ColumnInfo(name = "last_trained_at") val lastTrainedAt: Long?,
)
