// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

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
