// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import kotlinx.coroutines.flow.Flow

/**
 * Read-only access to personal-record (PR) aggregates. v2.1 leans on Room's native Flow
 * invalidation: every observe* call returns a query-backed Flow that re-emits when any of
 * the participating tables change. No manual cache; for one-shot freezes (Live workout
 * pre-session snapshot) collect with [kotlinx.coroutines.flow.firstOrNull] or call
 * [getPersonalRecord].
 *
 * None of these take an exercise type. Which sets are eligible and which one wins is decided
 * once, in SQL, from `exercise_table.type` — see `SessionDao`'s `PR_ELIGIBILITY` /
 * `PR_ORDER`. A caller-supplied type would be a second copy of that rule, free to go stale.
 */
interface PersonalRecordRepository {

    /**
     * The record-holding set for [exerciseUuid], or null when no finished session has logged
     * an eligible set yet.
     */
    suspend fun getPersonalRecord(exerciseUuid: String): PersonalRecordDataModel?

    /**
     * Reactive PR for a single exercise. Re-emits when finished-session sets for this
     * exercise change (Room handles invalidation). Subscribe in screens that should
     * stay live-updated; for one-shot freezes (e.g. Live workout pre-session snapshot)
     * use [observePersonalRecordsBatch] + `firstOrNull()` or stick to [getPersonalRecord].
     */
    fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDataModel?>

    /**
     * Batch PR map. Backed by a single Room query with `IN (:uuids)`; one subscription, one
     * query plan, one cursor. Re-emits when any participating table changes. Values are
     * non-null — exercises with no PR yet are simply absent from the map.
     */
    fun observePersonalRecordsBatch(
        exerciseUuids: Set<String>,
    ): Flow<Map<String, PersonalRecordDataModel>>

    /**
     * Projection of [observePersonalRecordsBatch] that only emits PR-holder set UUIDs. Used by
     * consumers (e.g. Past session) that need set-uuid equality for badge rendering and never
     * read other PR fields, so the heavier model never crosses the boundary.
     */
    fun observePrSetUuids(exerciseUuids: Set<String>): Flow<Set<String>>
}
