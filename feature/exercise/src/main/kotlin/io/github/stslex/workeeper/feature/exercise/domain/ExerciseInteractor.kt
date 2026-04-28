// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import android.net.Uri
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
internal interface ExerciseInteractor {

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun getLabels(exerciseUuid: String): List<String>

    suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int = DEFAULT_HISTORY_LIMIT,
    ): List<HistoryEntry>

    fun observeAvailableTags(): Flow<List<TagDataModel>>

    suspend fun saveExercise(snapshot: ExerciseChangeDataModel): SaveResult

    suspend fun createTag(name: String): TagDataModel

    suspend fun archive(uuid: String): ArchiveResult

    suspend fun restore(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    suspend fun permanentlyDelete(uuid: String)

    suspend fun getAdhocPlan(uuid: String): List<PlanSetDataModel>?

    suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDataModel>?)

    suspend fun clearWeightsFromAllPlansForExercise(uuid: String)

    suspend fun saveImage(uri: Uri, exerciseUuid: String): ImageSaveResult

    suspend fun createTempCaptureUri(): Uri

    suspend fun deleteImageFile(path: String): Boolean

    /**
     * Resolve whether an active session blocks Track now. Track now always creates a fresh
     * ad-hoc training, so the same-training silent-resume case used by Home and Training
     * detail does not apply — any active session means the user must choose how to
     * reconcile.
     */
    suspend fun resolveTrackNowConflict(): TrackNowConflict

    /**
     * Create an ad-hoc training that wraps [exerciseUuid] only, then start a session for it.
     * Returns the new session uuid for navigation. The training's name defaults to the
     * exercise name; the existing Live workout `loadSession` flow picks up
     * `exercise.last_adhoc_sets` as the plan.
     */
    suspend fun startTrackNowSession(exerciseUuid: String): String

    /**
     * Hard-deletes the in-progress session [sessionUuid], cascading through performed_
     * exercise + set rows via FK rules. Used by the Track now conflict resolver
     * "Delete & start new" branch.
     */
    suspend fun deleteSession(sessionUuid: String)

    sealed interface TrackNowConflict {

        data object ProceedFresh : TrackNowConflict

        data class NeedsUserChoice(val active: ActiveSessionInfo, val sessionLabel: String) :
            TrackNowConflict
    }

    sealed interface ArchiveResult {

        data object Success : ArchiveResult

        data class Blocked(val activeTrainings: List<String>) : ArchiveResult
    }

    sealed interface SaveResult {

        data class Success(val resolvedUuid: Uuid) : SaveResult

        data object DuplicateName : SaveResult
    }

    companion object {

        const val DEFAULT_HISTORY_LIMIT: Int = 5
    }
}
