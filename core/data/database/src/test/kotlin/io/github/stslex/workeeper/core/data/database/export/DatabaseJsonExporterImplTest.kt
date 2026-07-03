// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.export.model.ExerciseExportDto
import io.github.stslex.workeeper.core.data.database.export.model.ExerciseTypeExportDto
import io.github.stslex.workeeper.core.data.database.export.model.PerformedExerciseExportDto
import io.github.stslex.workeeper.core.data.database.export.model.PlanExerciseExportDto
import io.github.stslex.workeeper.core.data.database.export.model.PlanSetExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SessionExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SessionStateExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SetExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SetTypeExportDto
import io.github.stslex.workeeper.core.data.database.export.model.SourceExportDto
import io.github.stslex.workeeper.core.data.database.export.model.TrainingExportDto
import io.github.stslex.workeeper.core.data.database.export.model.WorkoutExportDto
import io.github.stslex.workeeper.core.data.database.migration.APP_DATABASE_VERSION
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.Instant

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class DatabaseJsonExporterImplTest : BaseDatabaseTest() {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var exporter: DatabaseJsonExporterImpl

    @BeforeEach
    fun setup() {
        initDb()
        exporter = DatabaseJsonExporterImpl(database, UnconfinedTestDispatcher())
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `export produces full nested graph including adhoc and archived rows`() = runTest {
        seedFullGraph()

        val raw = exporter.export(
            appVersion = "9.9.9",
            deviceModel = "Pixel Test",
            exportedAtEpochMs = EXPORTED_AT_EPOCH_MS,
        ).decodeToString()
        val dto = json.decodeFromString<WorkoutExportDto>(raw)

        assertEquals(1, dto.schemaVersion)
        assertEquals(Instant.ofEpochMilli(EXPORTED_AT_EPOCH_MS).toString(), dto.exportedAt)
        assertEquals("9.9.9", dto.source.appVersion)
        assertEquals(APP_DATABASE_VERSION, dto.source.dbSchemaVersion)
        assertEquals("Pixel Test", dto.source.deviceModel)

        // Correctness trap: adhoc + archived exercises MUST be present (unfiltered read).
        assertEquals(
            setOf("Bench", "Plank", "Adhoc Curl", "Archived Row"),
            dto.exercises.map { it.name }.toSet(),
        )
        assertTrue(dto.exercises.first { it.name == "Adhoc Curl" }.isAdhoc)
        assertTrue(dto.exercises.first { it.name == "Archived Row" }.archived)
        assertEquals(ExerciseTypeExportDto.WEIGHTLESS, dto.exercises.first { it.name == "Plank" }.type)

        // imagePath is never exported (device-local path, spec §7).
        assertFalse(raw.contains("imagePath"))
        assertFalse(raw.contains("bench.png"))

        // Archived training is included; top-level trainings are ordered by createdAt
        // (Old Split = 500 before Push = 1000) so the snapshot is stable across runs.
        assertEquals(listOf("Old Split", "Push"), dto.trainings.map { it.name })
        val old = dto.trainings.first { it.name == "Old Split" }
        assertTrue(old.plan.isEmpty())
        assertTrue(old.sessions.isEmpty())

        val push = dto.trainings.first { it.name == "Push" }
        assertEquals(listOf("push"), push.tags)

        // Plan rows in position order; plan-set types normalized WARMUP -> WARM.
        assertEquals(listOf("Bench", "Plank"), push.plan.map { it.exerciseName })
        val benchPlan = push.plan.first { it.exerciseName == "Bench" }
        assertEquals(listOf(SetTypeExportDto.WARM, SetTypeExportDto.WORK), benchPlan.planSets.map { it.type })
        assertTrue(push.plan.first { it.exerciseName == "Plank" }.planSets.isEmpty())

        // Sessions ordered by startedAt: FINISHED (10s) before IN_PROGRESS (20s).
        assertEquals(
            listOf(SessionStateExportDto.FINISHED, SessionStateExportDto.IN_PROGRESS),
            push.sessions.map { it.state },
        )
        val finished = push.sessions.first { it.state == SessionStateExportDto.FINISHED }
        assertEquals(listOf("Bench", "Plank"), finished.performedExercises.map { it.exerciseName })

        // WEIGHTLESS performed set: weight omitted -> null after decode.
        val plankSets = finished.performedExercises.first { it.exerciseName == "Plank" }.sets
        assertEquals(1, plankSets.size)
        assertNull(plankSets.first().weight)
    }

    @Test
    fun `empty database exports a valid envelope with empty arrays`() = runTest {
        val dto = json.decodeFromString<WorkoutExportDto>(
            exporter.export(appVersion = "1.0", deviceModel = null, exportedAtEpochMs = EXPORTED_AT_EPOCH_MS)
                .decodeToString(),
        )

        assertEquals(1, dto.schemaVersion)
        assertTrue(dto.exercises.isEmpty())
        assertTrue(dto.trainings.isEmpty())
        assertNull(dto.source.deviceModel)
    }

    @Test
    fun `enum round trip preserves all export enum values`() {
        val original = WorkoutExportDto(
            schemaVersion = 1,
            exportedAt = "2026-06-26T10:00:00Z",
            source = SourceExportDto(appVersion = "1.0", dbSchemaVersion = APP_DATABASE_VERSION, deviceModel = "Dev"),
            exercises = listOf(
                ExerciseExportDto(
                    uuid = "e1",
                    name = "Weighted",
                    type = ExerciseTypeExportDto.WEIGHTED,
                    isAdhoc = false,
                    archived = false,
                    createdAt = "2026-01-01T00:00:00Z",
                    tags = emptyList(),
                ),
                ExerciseExportDto(
                    uuid = "e2",
                    name = "Bodyweight",
                    type = ExerciseTypeExportDto.WEIGHTLESS,
                    isAdhoc = true,
                    archived = true,
                    createdAt = "2026-01-01T00:00:00Z",
                    tags = emptyList(),
                ),
            ),
            trainings = listOf(
                TrainingExportDto(
                    uuid = "t1",
                    name = "T",
                    isAdhoc = false,
                    archived = false,
                    createdAt = "2026-01-01T00:00:00Z",
                    tags = emptyList(),
                    plan = listOf(
                        PlanExerciseExportDto(
                            exerciseUuid = "e1",
                            exerciseName = "Weighted",
                            position = 0,
                            planSets = listOf(
                                PlanSetExportDto(reps = 5, weight = 100.0, type = SetTypeExportDto.WARM),
                                PlanSetExportDto(reps = 5, weight = 100.0, type = SetTypeExportDto.WORK),
                                PlanSetExportDto(reps = 3, weight = 110.0, type = SetTypeExportDto.FAIL),
                                PlanSetExportDto(reps = 8, weight = null, type = SetTypeExportDto.DROP),
                            ),
                        ),
                    ),
                    sessions = listOf(
                        SessionExportDto(
                            uuid = "s1",
                            state = SessionStateExportDto.FINISHED,
                            startedAt = "2026-01-01T00:00:00Z",
                            finishedAt = "2026-01-01T01:00:00Z",
                            performedExercises = listOf(
                                PerformedExerciseExportDto(
                                    exerciseUuid = "e1",
                                    exerciseName = "Weighted",
                                    position = 0,
                                    skipped = false,
                                    sets = listOf(
                                        SetExportDto(
                                            position = 0,
                                            reps = 5,
                                            weight = 100.0,
                                            type = SetTypeExportDto.WORK,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SessionExportDto(
                            uuid = "s2",
                            state = SessionStateExportDto.IN_PROGRESS,
                            startedAt = "2026-01-02T00:00:00Z",
                            finishedAt = null,
                            performedExercises = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<WorkoutExportDto>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    private suspend fun seedFullGraph() {
        val push = TrainingEntity(
            name = "Push",
            description = "Heavy",
            isAdhoc = false,
            archived = false,
            createdAt = 1_000L,
            archivedAt = null,
        )
        val oldSplit = TrainingEntity(
            name = "Old Split",
            description = null,
            isAdhoc = false,
            archived = true,
            createdAt = 500L,
            archivedAt = 2_000L,
        )
        listOf(push, oldSplit).forEach { database.trainingDao.insert(it) }

        val bench = exercise(name = "Bench", imagePath = "/data/user/0/app/files/bench.png")
        val plank = exercise(name = "Plank", type = ExerciseTypeEntity.WEIGHTLESS)
        val adhocCurl = exercise(name = "Adhoc Curl", isAdhoc = true)
        val archivedRow = exercise(name = "Archived Row", archived = true, archivedAt = 3_000L)
        listOf(bench, plank, adhocCurl, archivedRow).forEach { database.exerciseDao.insert(it) }

        val benchPlanJson = PlanSetsConverter.toJson(
            listOf(
                PlanSetDataModel(weight = 60.0, reps = 10, type = SetTypeDataModel.WARMUP),
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
            ),
        )
        database.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(push.uuid, bench.uuid, position = 0, planSets = benchPlanJson),
                TrainingExerciseEntity(push.uuid, plank.uuid, position = 1, planSets = null),
            ),
        )

        val finished = SessionEntity(
            trainingUuid = push.uuid,
            state = SessionStateEntity.FINISHED,
            startedAt = 10_000L,
            finishedAt = 11_000L,
        )
        val active = SessionEntity(
            trainingUuid = push.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 20_000L,
            finishedAt = null,
        )
        listOf(finished, active).forEach { database.sessionDao.insert(it) }

        val benchPerformed = PerformedExerciseEntity(
            sessionUuid = finished.uuid,
            exerciseUuid = bench.uuid,
            position = 0,
            skipped = false,
        )
        val plankPerformed = PerformedExerciseEntity(
            sessionUuid = finished.uuid,
            exerciseUuid = plank.uuid,
            position = 1,
            skipped = false,
        )
        listOf(benchPerformed, plankPerformed).forEach { database.performedExerciseDao.insert(it) }

        database.setDao.insert(
            SetEntity(
                performedExerciseUuid = benchPerformed.uuid,
                position = 0,
                reps = 5,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
        )
        database.setDao.insert(
            SetEntity(
                performedExerciseUuid = plankPerformed.uuid,
                position = 0,
                reps = 30,
                weight = null,
                type = SetTypeEntity.WORK,
            ),
        )

        val pushTag = TagEntity(name = "push")
        val barbell = TagEntity(name = "barbell")
        val core = TagEntity(name = "core")
        listOf(pushTag, barbell, core).forEach { database.tagDao.insert(it) }
        database.exerciseTagDao.insert(
            listOf(
                ExerciseTagEntity(bench.uuid, pushTag.uuid),
                ExerciseTagEntity(bench.uuid, barbell.uuid),
                ExerciseTagEntity(plank.uuid, core.uuid),
            ),
        )
        database.trainingTagDao.insert(listOf(TrainingTagEntity(push.uuid, pushTag.uuid)))
    }

    private fun exercise(
        name: String,
        type: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
        imagePath: String? = null,
        isAdhoc: Boolean = false,
        archived: Boolean = false,
        archivedAt: Long? = null,
    ) = ExerciseEntity(
        name = name,
        type = type,
        description = null,
        imagePath = imagePath,
        archived = archived,
        createdAt = 1_000L,
        archivedAt = archivedAt,
        lastAdhocSets = null,
        isAdhoc = isAdhoc,
    )

    private companion object {
        const val EXPORTED_AT_EPOCH_MS = 1_700_000_000_000L
    }
}
