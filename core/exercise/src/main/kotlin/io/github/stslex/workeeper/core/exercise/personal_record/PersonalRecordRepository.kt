// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.personal_record

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import kotlinx.coroutines.flow.Flow

/**
 * Read-only access to personal-record (PR) aggregates. v2.1 leans on Room's native Flow
 * invalidation: every observe* call returns a query-backed Flow that re-emits when any of
 * the participating tables change. No manual cache; for one-shot freezes (Live workout
 * pre-session snapshot) collect with [kotlinx.coroutines.flow.firstOrNull] or call
 * [getPersonalRecord].
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

    /**
     * Reactive PR for a single exercise. Re-emits when finished-session sets for this
     * exercise change (Room handles invalidation). Subscribe in screens that should
     * stay live-updated; for one-shot freezes (e.g. Live workout pre-session snapshot)
     * use [observePersonalRecords] + `firstOrNull()` or stick to [getPersonalRecord].
     */
    fun observePersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): Flow<PersonalRecordDataModel?>

    /**
     * Reactive PR map for a batch of exercises. Implemented via [kotlinx.coroutines.flow.combine]
     * over per-exercise flows; downstream gets a fresh map each time any contributor emits.
     * The map's keyset matches [uuidsByType] exactly; values are nullable when the exercise
     * has no PR yet.
     */
    fun observePersonalRecords(
        uuidsByType: Map<String, ExerciseTypeDataModel>,
    ): Flow<Map<String, PersonalRecordDataModel?>>
}
