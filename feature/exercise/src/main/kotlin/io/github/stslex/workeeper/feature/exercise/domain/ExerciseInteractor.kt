// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import android.net.Uri
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
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

    /**
     * Reactive PR for the exercise. Re-emits when finished-session sets for [exerciseUuid]
     * change (Room invalidation). Drives the read-mode PR card; collected only when the
     * screen is bound to an existing exercise (create mode has no uuid yet).
     */
    fun observePersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): Flow<PersonalRecordDataModel?>

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
     * Returns the new session uuid for navigation. Delegates to
     * `SessionRepository.createAdhocSession` — same data path as v2.3 Quick start. Plan
     * rows are persisted with `plan_sets = null`; the existing `loadSession` fallback
     * (`exercise.last_adhoc_sets`) populates the editor.
     */
    suspend fun startTrackNowSession(exerciseUuid: String): String

    /**
     * Cancel the in-progress session [sessionUuid]. Branches on training type so an ad-hoc
     * training created for Track Now (or v2.3 Quick start) is cascade-deleted alongside
     * its inline-created exercises, while a library training session is just unlinked.
     * Replaces the older session-only delete that leaked the parent ad-hoc training row —
     * this is the bug the v5 → v6 retroactive sweep cleans up history of.
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
