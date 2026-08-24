// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import kotlinx.coroutines.flow.Flow

/**
 * Read-only personal-record aggregates. Every `observe*` is a Room-Flow that re-emits on change.
 * Eligibility and ordering live once in SQL — `SessionDao`'s `PR_ELIGIBILITY` / `PR_ORDER`.
 */
interface PersonalRecordRepository {

    /** The record-holding set for [exerciseUuid], or null when no finished session logged one. */
    suspend fun getPersonalRecord(exerciseUuid: String): PersonalRecordDataModel?

    /** Reactive PR for one exercise; re-emits when its finished-session sets change. */
    fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDataModel?>

    /** Batch PR map from one `IN (:uuids)` query; exercises with no PR are absent, not null. */
    fun observePersonalRecordsBatch(
        exerciseUuids: Set<String>,
    ): Flow<Map<String, PersonalRecordDataModel>>

    /** [observePersonalRecordsBatch] projected to PR-holder set UUIDs only (badge rendering). */
    fun observePrSetUuids(exerciseUuids: Set<String>): Flow<Set<String>>
}
