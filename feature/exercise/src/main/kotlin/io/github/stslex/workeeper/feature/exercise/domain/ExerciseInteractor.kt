// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TagDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface ExerciseInteractor {

    suspend fun getExercise(uuid: String): ExerciseDomain?

    suspend fun getLabels(exerciseUuid: String): List<String>

    suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int = DEFAULT_HISTORY_LIMIT,
    ): List<HistoryEntryDomain>

    /**
     * Total finished sessions containing this exercise (История section head count). May exceed
     * the row list: that additionally filters sessions with no logged sets.
     */
    suspend fun countSessions(exerciseUuid: String): Int

    fun observeAvailableTags(): Flow<List<TagDomain>>

    /** Reactive PR for the exercise; collected only when the screen is bound to a saved one. */
    fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDomain?>

    suspend fun saveExercise(snapshot: ExerciseChangeDomain): SaveResult

    suspend fun createTag(name: String): TagDomain

    suspend fun archive(uuid: String): ArchiveResult

    suspend fun restore(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    suspend fun permanentlyDelete(uuid: String)

    suspend fun getAdhocPlan(uuid: String): List<PlanSetDomain>?

    suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDomain>?)

    suspend fun saveImage(ref: ImageRef, exerciseUuid: String): ImageSaveResult

    suspend fun createTempCaptureRef(): ImageRef

    suspend fun deleteImageFile(path: String): Boolean

    /**
     * Resolve whether an active session blocks Track now. Unlike Home and Training detail there is
     * no silent-resume case — Track now always creates a fresh ad-hoc training.
     */
    suspend fun resolveTrackNowConflict(): TrackNowConflict

    /**
     * Create an ad-hoc training wrapping [exerciseUuid] only, start a session for it and return
     * the session uuid. Plan rows persist with `plan_sets = null`; `loadSession` falls back.
     */
    suspend fun startTrackNowSession(
        exerciseUuid: String,
        defaultName: String,
    ): String

    /**
     * Cancel the in-progress session [sessionUuid]. Branches on training type so an ad-hoc
     * training is cascade-deleted with its inline-created exercises, a library one just unlinked.
     */
    suspend fun deleteSession(sessionUuid: String)

    companion object {

        const val DEFAULT_HISTORY_LIMIT: Int = 5
    }
}
