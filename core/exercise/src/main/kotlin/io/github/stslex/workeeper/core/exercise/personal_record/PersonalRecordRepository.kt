// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.personal_record

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel

/**
 * Read-only access to personal-record (PR) aggregates. v2.0 has no caching layer; every call
 * runs the underlying SQL aggregate against finished sessions. The signature exists so the
 * v2.1 PR detector and Exercise detail PR block can hit a stable surface.
 */
interface PersonalRecordRepository {

    /**
     * Returns the heaviest set logged for [exerciseUuid] across finished sessions, or null
     * when no finished session has logged the exercise yet. The aggregate respects [type]:
     * weighted exercises sort by weight DESC then reps DESC; weightless exercises sort by
     * reps DESC. Tiebreak: earliest `finished_at` wins.
     */
    suspend fun getPersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): PersonalRecordDataModel?
}
