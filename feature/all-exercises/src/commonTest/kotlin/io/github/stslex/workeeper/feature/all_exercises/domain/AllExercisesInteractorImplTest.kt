// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class AllExercisesInteractorImplTest {

    private val exerciseRepository = RecordingExerciseRepository()
    private val tagRepository = UnreachedTagRepository()
    private val interactor = AllExercisesInteractorImpl(
        exerciseRepository = exerciseRepository,
        tagRepository = tagRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `archiveExercise returns Success when no active trainings`() = runTest {
        exerciseRepository.activeTrainingsUsing["uuid-1"] = emptyList()

        val result = interactor.archiveExercise("uuid-1")

        assertTrue(result is ArchiveResult.Success)
        assertContains(exerciseRepository.archiveCalls, "uuid-1")
    }

    @Test
    fun `archiveExercise returns Blocked when active trainings exist`() = runTest {
        exerciseRepository.activeTrainingsUsing["uuid-1"] = listOf("Push", "Pull")

        val result = interactor.archiveExercise("uuid-1")

        assertTrue(result is ArchiveResult.Blocked)
        assertEquals(listOf("Push", "Pull"), (result as ArchiveResult.Blocked).activeTrainings)
        assertEquals(emptyList(), exerciseRepository.archiveCalls)
    }

    @Test
    fun `restoreExercise delegates to repository`() = runTest {
        interactor.restoreExercise("uuid-1")
        assertContains(exerciseRepository.restoreCalls, "uuid-1")
    }

    @Test
    fun `canPermanentlyDelete delegates to repository`() = runTest {
        exerciseRepository.canPermanentlyDeleteImmediately["uuid-1"] = true
        assertTrue(interactor.canPermanentlyDelete("uuid-1"))
        assertContains(exerciseRepository.canPermanentlyDeleteCalls, "uuid-1")
    }

    @Test
    fun `permanentlyDelete delegates to repository`() = runTest {
        interactor.permanentlyDelete("uuid-1")
        assertContains(exerciseRepository.permanentDeleteCalls, "uuid-1")
    }
}

private fun unreached(member: String): Nothing =
    error("$member is not reached by AllExercisesInteractorImplTest")

/**
 * The exercise repository as the relaxed MockK mock answered these identities: the four writes
 * and the permanent-delete check record their argument, and the two reads answer what a test
 * set, else the relaxed default (no active trainings, not deletable). Members these identities
 * never reach fail fast.
 */
private class RecordingExerciseRepository : ExerciseRepository {

    val activeTrainingsUsing = mutableMapOf<String, List<String>>()
    val canPermanentlyDeleteImmediately = mutableMapOf<String, Boolean>()
    val archiveCalls = mutableListOf<String>()
    val restoreCalls = mutableListOf<String>()
    val permanentDeleteCalls = mutableListOf<String>()
    val canPermanentlyDeleteCalls = mutableListOf<String>()

    override suspend fun getActiveTrainingsUsing(exerciseUuid: String): List<String> =
        activeTrainingsUsing[exerciseUuid] ?: emptyList()

    override suspend fun archive(uuid: String) {
        archiveCalls.add(uuid)
    }

    override suspend fun restore(uuid: String) {
        restoreCalls.add(uuid)
    }

    override suspend fun permanentDelete(uuid: String) {
        permanentDeleteCalls.add(uuid)
    }

    override suspend fun canPermanentlyDeleteImmediately(uuid: String): Boolean {
        canPermanentlyDeleteCalls.add(uuid)
        return canPermanentlyDeleteImmediately[uuid] ?: false
    }

    override val exercises: Nothing get() = unreached("exercises")

    override fun getUniqueExercises(query: String): Nothing = unreached("getUniqueExercises")

    override suspend fun getExercisesByUuid(uuids: List<String>): Nothing =
        unreached("getExercisesByUuid")

    override suspend fun getAdhocPlans(uuids: List<String>): Nothing = unreached("getAdhocPlans")

    override suspend fun getExercise(uuid: String): Nothing = unreached("getExercise")

    override suspend fun saveItem(item: ExerciseChangeDataModel): Nothing = unreached("saveItem")

    override suspend fun createInlineAdhocExercise(name: String): Nothing =
        unreached("createInlineAdhocExercise")

    override suspend fun getAdhocPlan(exerciseUuid: String): Nothing = unreached("getAdhocPlan")

    override suspend fun setAdhocPlan(
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    ): Nothing = unreached("setAdhocPlan")

    override suspend fun setExerciseType(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): Nothing = unreached("setExerciseType")

    override suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String): Nothing =
        unreached("clearWeightsFromAllPlansForExercise")

    override suspend fun deleteItem(uuid: String): Nothing = unreached("deleteItem")

    override suspend fun searchActiveExercises(
        query: String,
        excludeUuids: Set<String>,
    ): Nothing = unreached("searchActiveExercises")

    override suspend fun deleteAllItems(uuids: List<Uuid>): Nothing = unreached("deleteAllItems")

    override suspend fun canArchive(uuid: String): Nothing = unreached("canArchive")

    override fun pagedArchived(): Nothing = unreached("pagedArchived")

    override fun observeArchivedCount(): Nothing = unreached("observeArchivedCount")

    override suspend fun countSessionsUsing(exerciseUuid: String): Nothing =
        unreached("countSessionsUsing")

    override fun observeLinkedTrainingsCount(exerciseUuid: String): Nothing =
        unreached("observeLinkedTrainingsCount")

    override fun observeLastTrainedAt(exerciseUuid: String): Nothing =
        unreached("observeLastTrainedAt")

    override fun pagedActiveByTags(tagUuids: Set<String>): Nothing = unreached("pagedActiveByTags")

    override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing =
        unreached("pagedActiveWithStats")

    override suspend fun getRecentHistory(exerciseUuid: String, limit: Int): Nothing =
        unreached("getRecentHistory")

    override suspend fun getLastTrainedExerciseUuid(): Nothing =
        unreached("getLastTrainedExerciseUuid")

    override suspend fun getRecentlyTrainedExercises(): Nothing =
        unreached("getRecentlyTrainedExercises")

    override suspend fun getLabels(exerciseUuid: String): Nothing = unreached("getLabels")

    override suspend fun bulkArchive(uuids: Set<String>): Nothing = unreached("bulkArchive")

    override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing =
        unreached("bulkPermanentDelete")

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing =
        unreached("canBulkPermanentDelete")
}

/** No identity here reaches the tag repository. */
private class UnreachedTagRepository : TagRepository {

    override fun observeAll(): Nothing = unreached("observeAll")

    override suspend fun searchByPrefix(prefix: String): Nothing = unreached("searchByPrefix")

    override suspend fun findByName(name: String): Nothing = unreached("findByName")

    override suspend fun add(name: String): Nothing = unreached("add")

    override fun observeTagIdleStats(limit: Int): Nothing = unreached("observeTagIdleStats")
}
