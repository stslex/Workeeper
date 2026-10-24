// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.domain.usecase.ArchiveExerciseUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.DeleteSessionUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.ResolveTrackNowConflictUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.StartTrackNowSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ExerciseInteractorImplTest {

    private val exerciseRepository = RecordingExerciseRepository()
    private val tagRepository = UnreachedTagRepository()
    private val imageStorage = RecordingImageStorage()
    private val personalRecordRepository = RecordingPersonalRecordRepository()
    private val archiveExerciseUseCase = RecordingArchiveExerciseUseCase()
    private val resolveTrackNowConflictUseCase = UnreachedResolveTrackNowConflictUseCase()
    private val startTrackNowSessionUseCase = UnreachedStartTrackNowSessionUseCase()
    private val deleteSessionUseCase = UnreachedDeleteSessionUseCase()
    private val interactor = ExerciseInteractorImpl(
        exerciseRepository = exerciseRepository,
        tagRepository = tagRepository,
        imageStorage = imageStorage,
        personalRecordRepository = personalRecordRepository,
        archiveExerciseUseCase = archiveExerciseUseCase,
        resolveTrackNowConflictUseCase = resolveTrackNowConflictUseCase,
        startTrackNowSessionUseCase = startTrackNowSessionUseCase,
        deleteSessionUseCase = deleteSessionUseCase,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `countSessions forwards to countSessionsUsing`() = runTest {
        exerciseRepository.sessionCounts["uuid-1"] = 4

        assertEquals(4, interactor.countSessions("uuid-1"))

        assertEquals(listOf("uuid-1"), exerciseRepository.countSessionsCalls)
    }

    @Test
    fun `saveExercise forwards the snapshot uuid to repository on Success`() = runTest {
        exerciseRepository.saveResult = ExerciseRepository.SaveResult.Success

        val uuid = Uuid.random()
        val result = interactor.saveExercise(
            ExerciseChangeDomain(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeDomain.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                timestamp = 0L,
                labels = emptyList(),
                lastAdhocSets = null,
            ),
        )

        assertTrue(result is SaveResult.Success)
        assertEquals(uuid, (result as SaveResult.Success).resolvedUuid)
        assertEquals(uuid, exerciseRepository.savedItems.single().uuid)
        assertTrue(exerciseRepository.savedItems.isNotEmpty())
    }

    @Test
    fun `saveExercise propagates the lastAdhocSets payload`() = runTest {
        exerciseRepository.saveResult = ExerciseRepository.SaveResult.Success

        interactor.saveExercise(
            ExerciseChangeDomain(
                uuid = Uuid.random(),
                name = "Bench",
                type = ExerciseTypeDomain.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                timestamp = 0L,
                labels = emptyList(),
                lastAdhocSets = null,
            ),
        )

        assertEquals(null, exerciseRepository.savedItems.single().lastAdHocSets)
    }

    @Test
    fun `saveExercise propagates DuplicateName from repository`() = runTest {
        exerciseRepository.saveResult = ExerciseRepository.SaveResult.DuplicateName

        val result = interactor.saveExercise(
            ExerciseChangeDomain(
                uuid = Uuid.random(),
                name = "Bench",
                type = ExerciseTypeDomain.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                timestamp = 0L,
                labels = emptyList(),
                lastAdhocSets = null,
            ),
        )

        assertEquals(SaveResult.DuplicateName, result)
    }

    @Test
    fun `archive returns Blocked when active trainings exist`() = runTest {
        archiveExerciseUseCase.results["uuid-1"] = ArchiveResult.Blocked(listOf("Push"))

        val result = interactor.archive("uuid-1")
        assertTrue(result is ArchiveResult.Blocked)
        assertTrue("uuid-1" in archiveExerciseUseCase.calls)
    }

    @Test
    fun `archive returns Success when no active trainings`() = runTest {
        archiveExerciseUseCase.results["uuid-1"] = ArchiveResult.Success

        val result = interactor.archive("uuid-1")
        assertTrue(result is ArchiveResult.Success)
        assertTrue("uuid-1" in archiveExerciseUseCase.calls)
    }

    @Test
    fun `getImageLastModified forwards to the image storage`() = runTest {
        imageStorage.lastModified["/files/x.jpg"] = 1234L

        assertEquals(1234L, interactor.getImageLastModified("/files/x.jpg"))

        assertEquals(listOf("/files/x.jpg"), imageStorage.lastModifiedCalls)
    }

    @Test
    fun `canPermanentlyDelete delegates to repository`() = runTest {
        exerciseRepository.permanentlyDeletable["uuid-1"] = true
        assertTrue(interactor.canPermanentlyDelete("uuid-1"))
    }

    @Test
    fun `permanentlyDelete delegates to repository`() = runTest {
        interactor.permanentlyDelete("uuid-1")
        assertTrue("uuid-1" in exerciseRepository.permanentDeleteCalls)
    }

    @Test
    fun `observePersonalRecord forwards to repository and maps to domain`() = runTest {
        val payload = PersonalRecordDataModel(
            sessionUuid = "session",
            performedExerciseUuid = "performed",
            setUuid = "set",
            weight = 100.0,
            reps = 5,
            type = SetsDataType.WORK,
            finishedAt = 1_000L,
        )
        personalRecordRepository.records["uuid-1"] = flowOf(payload)

        val result = interactor
            .observePersonalRecord("uuid-1")
            .first()

        assertNotNull(result)
        assertEquals("session", result?.sessionUuid)
        assertEquals(100.0, result?.weight)
        assertEquals(5, result?.reps)
        assertEquals(SetTypeDomain.WORK, result?.type)
    }

    /**
     * The repository as the relaxed MockK one answered these identities: the four members they
     * reach answer what an identity set up, else the relaxed default (`0`, `false`), and record
     * their calls; the save records every item it was handed. Every other member fails fast.
     */
    private class RecordingExerciseRepository : ExerciseRepository {

        val sessionCounts = mutableMapOf<String, Int>()
        val countSessionsCalls = mutableListOf<String>()
        var saveResult: ExerciseRepository.SaveResult? = null
        val savedItems = mutableListOf<ExerciseChangeDataModel>()
        val permanentlyDeletable = mutableMapOf<String, Boolean>()
        val permanentDeleteCalls = mutableListOf<String>()

        override suspend fun countSessionsUsing(exerciseUuid: String): Int {
            countSessionsCalls.add(exerciseUuid)
            return sessionCounts[exerciseUuid] ?: 0
        }

        override suspend fun saveItem(item: ExerciseChangeDataModel): ExerciseRepository.SaveResult {
            savedItems.add(item)
            return saveResult ?: error("saveItem was not set up")
        }

        override suspend fun canPermanentlyDeleteImmediately(uuid: String): Boolean =
            permanentlyDeletable[uuid] ?: false

        override suspend fun permanentDelete(uuid: String) {
            permanentDeleteCalls.add(uuid)
        }

        override val exercises: Nothing get() = unreached("exercises")

        override fun getUniqueExercises(query: String): Nothing = unreached("getUniqueExercises")

        override suspend fun getExercisesByUuid(uuids: List<String>): Nothing =
            unreached("getExercisesByUuid")

        override suspend fun getAdhocPlans(uuids: List<String>): Nothing = unreached("getAdhocPlans")

        override suspend fun getExercise(uuid: String): Nothing = unreached("getExercise")

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

        override suspend fun archive(uuid: String): Nothing = unreached("archive")

        override suspend fun restore(uuid: String): Nothing = unreached("restore")

        override suspend fun canArchive(uuid: String): Nothing = unreached("canArchive")

        override suspend fun getActiveTrainingsUsing(exerciseUuid: String): Nothing =
            unreached("getActiveTrainingsUsing")

        override fun pagedArchived(): Nothing = unreached("pagedArchived")

        override fun observeArchivedCount(): Nothing = unreached("observeArchivedCount")

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

    /** `lastModified` answers what an identity set up (else `0`) and records its paths. */
    private class RecordingImageStorage : ImageStorage {

        val lastModified = mutableMapOf<String, Long>()
        val lastModifiedCalls = mutableListOf<String>()

        override suspend fun lastModified(path: String): Long {
            lastModifiedCalls.add(path)
            return lastModified[path] ?: 0L
        }

        override suspend fun saveImage(sourceRef: ImageRef, exerciseUuid: String): Nothing =
            unreached("saveImage")

        override suspend fun createTempCaptureRef(): Nothing = unreached("createTempCaptureRef")

        override suspend fun deleteImage(path: String): Nothing = unreached("deleteImage")

        override suspend fun cleanupTempFiles(): Nothing = unreached("cleanupTempFiles")
    }

    /** `observePersonalRecord` answers the flow an identity set up; the rest fail fast. */
    private class RecordingPersonalRecordRepository : PersonalRecordRepository {

        val records = mutableMapOf<String, Flow<PersonalRecordDataModel?>>()

        override fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDataModel?> =
            records[exerciseUuid] ?: error("observePersonalRecord($exerciseUuid) was not set up")

        override suspend fun getPersonalRecord(exerciseUuid: String): Nothing = unreached("getPersonalRecord")

        override fun observePersonalRecordsBatch(exerciseUuids: Set<String>): Nothing =
            unreached("observePersonalRecordsBatch")

        override fun observePrSetUuids(exerciseUuids: Set<String>): Nothing = unreached("observePrSetUuids")
    }

    /** The archive use case answers what an identity set up and records every uuid it is handed. */
    private class RecordingArchiveExerciseUseCase : ArchiveExerciseUseCase {

        val results = mutableMapOf<String, ArchiveResult>()
        val calls = mutableListOf<String>()

        override suspend operator fun invoke(uuid: String): ArchiveResult {
            calls.add(uuid)
            return results[uuid] ?: error("archive($uuid) was not set up")
        }
    }

    private class UnreachedResolveTrackNowConflictUseCase : ResolveTrackNowConflictUseCase {

        override suspend operator fun invoke(): TrackNowConflict = unreached("resolveTrackNowConflict")
    }

    private class UnreachedStartTrackNowSessionUseCase : StartTrackNowSessionUseCase {

        override suspend operator fun invoke(exerciseUuid: String, defaultName: String): String =
            unreached("startTrackNowSession")
    }

    private class UnreachedDeleteSessionUseCase : DeleteSessionUseCase {

        override suspend operator fun invoke(sessionUuid: String): Unit = unreached("deleteSession")
    }

    private companion object {

        fun unreached(member: String): Nothing = error("no identity here reaches $member")
    }
}
