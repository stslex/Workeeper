// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.PrRuleDbSeeder
import io.github.stslex.workeeper.core.data.database.testfixtures.PrRuleFixture
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.sets.PrComparator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import kotlin.uuid.Uuid

/**
 * One [PrRuleFixture] scenario set through all four in-repo PR sites, which must agree.
 * See documentation/feature-specs/v2.1-pr-tracking.md for the site inventory.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class PrRuleParityTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var seeder: PrRuleDbSeeder
    private lateinit var repository: PersonalRecordRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        seeder = PrRuleDbSeeder(env)
        repository = PersonalRecordRepositoryImpl(
            sessionDao = env.sessionDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `every PR site names the same set`() = runTest {
        PrRuleFixture.SCENARIOS.forEach { scenario -> assertAllSitesAgree(scenario) }
    }

    private suspend fun assertAllSitesAgree(scenario: PrRuleFixture.PrScenario) {
        val where = "[${scenario.name}] ${scenario.why}"
        val seeded = seeder.seed(scenario, state = SessionStateEntity.IN_PROGRESS)
        val exerciseUuid = seeded.exerciseUuid
        val type = if (scenario.isWeightless) {
            ExerciseTypeDataModel.WEIGHTLESS
        } else {
            ExerciseTypeDataModel.WEIGHTED
        }

        // Phase 1 — the session is still running, so SQL is blind to it by design.
        assertNull(
            env.sessionDao.getPersonalRecord(exerciseUuid),
            "$where — S1 must not award a record from an unfinished session",
        )
        assertNull(
            env.sessionDao.observePersonalRecord(exerciseUuid).first(),
            "$where — S2 must not award a record from an unfinished session",
        )
        assertTrue(
            env.sessionDao.observePersonalRecordsBatch(listOf(exerciseUuid)).first().isEmpty(),
            "$where — S3 must not award a record from an unfinished session",
        )

        // S4 answers now; the SQL sites must reach the same set once the session lands.
        val planSets = scenario.candidates.map { it.toPlanSet() }
        val best = PrComparator.bestOf(planSets, type)
        // GUARD: identity, not equality — tie scenarios hold candidates equal on (weight, reps).
        val bestLabel = planSets
            .indexOfFirst { it === best }
            .takeIf { it >= 0 }
            ?.let { scenario.candidates[it].label }
        assertEquals(scenario.expectedHolder, bestLabel, "$where — S4 PrComparator.bestOf")

        // Phase 2 — the session finishes; every SQL site must now name that same set.
        seeder.finishAllSessions(seeded)
        val expected = seeded.expectedHolderSetUuid

        assertEquals(
            expected,
            env.sessionDao.getPersonalRecord(exerciseUuid)?.setUuid,
            "$where — S1 SessionDao.getPersonalRecord",
        )
        assertEquals(
            expected,
            env.sessionDao.observePersonalRecord(exerciseUuid).first()?.setUuid,
            "$where — S2 SessionDao.observePersonalRecord",
        )
        assertEquals(
            expected,
            repository.observePersonalRecordsBatch(setOf(exerciseUuid.toString()))
                .first()[exerciseUuid.toString()]
                ?.setUuid
                ?.let(Uuid::parse),
            "$where — S3 observePersonalRecordsBatch via the repository",
        )
        assertEquals(
            setOfNotNull(expected?.toString()),
            repository.observePrSetUuids(setOf(exerciseUuid.toString())).first(),
            "$where — S3 observePrSetUuids via the repository",
        )
    }

    @Test
    fun `a residual weight on a weightless row does not promote it in the batch query`() = runTest {
        // Regression: the batch query once ordered weightless rows by a stray weight, not reps.
        val scenario = PrRuleFixture.WEIGHTLESS_WITH_RESIDUAL_WEIGHTS
        val seeded = seeder.seed(scenario, state = SessionStateEntity.FINISHED)

        val rows = env.sessionDao.observePersonalRecordsBatch(listOf(seeded.exerciseUuid)).first()

        assertEquals(
            listOf("no-weight-12-reps", "residual-weight-8-reps"),
            rows.map { seeded.labelOf(it.setUuid) },
            "batch candidates must come back rep-ordered, best first — the stray 50kg row loses",
        )
    }

    @Test
    fun `beats hands ties to the incumbent because a live session finishes last`() {
        val equal = PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK)
        val better = PlanSetDataModel(weight = 100.0, reps = 6, type = SetTypeDataModel.WORK)
        val zeroRep = PlanSetDataModel(weight = 500.0, reps = 0, type = SetTypeDataModel.WORK)

        assertFalse(
            PrComparator.beats(
                candidate = equal,
                baselineWeight = 100.0,
                baselineReps = 5,
                type = ExerciseTypeDataModel.WEIGHTED,
                hasBaseline = true,
            ),
            "equalling the record does not take it — the live session's finished_at is the largest",
        )
        assertTrue(
            PrComparator.beats(
                candidate = better,
                baselineWeight = 100.0,
                baselineReps = 5,
                type = ExerciseTypeDataModel.WEIGHTED,
                hasBaseline = true,
            ),
        )
        assertFalse(
            PrComparator.beats(
                candidate = zeroRep,
                baselineWeight = null,
                baselineReps = null,
                type = ExerciseTypeDataModel.WEIGHTED,
                hasBaseline = false,
            ),
            "a zero-rep set is ineligible, so it cannot open an empty record either",
        )
    }

    private fun PrRuleFixture.PrCandidate.toPlanSet(): PlanSetDataModel = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = SetTypeDataModel.WORK,
    )
}
