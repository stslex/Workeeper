// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import io.github.stslex.workeeper.core.data.database.testfixtures.PrRuleFixture
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistorySetDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * The fifth PR site. `ChartFolder` picks one set to represent a day, and that choice is the
 * same question the four data-layer sites answer — so it is held to the same scenarios, from
 * the same [PrRuleFixture], as `PrRuleParityTest` in `core/data/exercise`. This module has no
 * database on its test classpath, hence a separate test rather than a fifth arm over there;
 * the fixture is shared, which is the part that matters.
 *
 * Every fixture timestamp falls on the same day, so each scenario folds to exactly one point
 * and "the day's set" is "the record-holding set" — which is the parity claim.
 *
 * The parity claim is asserted under [ChartMetricDomain.HEAVIEST_WEIGHT], because that is the
 * metric under which the chart's primary sort key *is* the PR rule's primary key. Under
 * [ChartMetricDomain.VOLUME_PER_SET] the chart deliberately ranks by `weight × reps` instead:
 * eligibility and the lower `finishedAt` / position tiebreaks are still shared, but the reps
 * key is not. A volume tie is a trade of weight against reps, so ranking by reps there would
 * amount to ranking by *ascending* weight. The one test below that does pass
 * [ChartMetricDomain.VOLUME_PER_SET] therefore claims only the shared eligibility, not the
 * winner.
 */
internal class ChartFolderPrRuleParityTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `the day's set is the record-holding set`() {
        PrRuleFixture.SCENARIOS.forEach { scenario ->
            val where = "[${scenario.name}] ${scenario.why}"
            val fold = bucketAndFold(
                history = scenario.toHistory(),
                preset = ChartPresetDomain.ALL,
                metric = ChartMetricDomain.HEAVIEST_WEIGHT,
                exerciseType = if (scenario.isWeightless) {
                    ExerciseTypeDomain.WEIGHTLESS
                } else {
                    ExerciseTypeDomain.WEIGHTED
                },
                now = NOW,
                zoneId = zone,
            )

            val expected = scenario.expectedHolder
            if (expected == null) {
                assertTrue(
                    fold.points.isEmpty(),
                    "$where — nothing is eligible, so the day must not plot a point at all",
                )
                return@forEach
            }

            val holder = scenario.candidates.first { it.label == expected }
            assertEquals(1, fold.points.size, "$where — one day, one point")
            val point = fold.points.single()
            assertEquals(sessionUuidFor(holder.finishedAt), point.sessionUuid, "$where — session")
            assertEquals(holder.weight, point.weight, "$where — weight")
            assertEquals(holder.reps, point.reps, "$where — reps")
        }
    }

    @Test
    fun `an ineligible set never becomes the day's point`() {
        // Before the fix, a weight-null set on a WEIGHTED exercise was coerced to 0.0 rather
        // than excluded, so an exercise whose sets all lack a weight plotted a flat run of
        // zeroes instead of plotting nothing.
        val scenario = PrRuleFixture.WEIGHTED_WITH_NO_WEIGHTS_HAS_NO_RECORD

        val fold = bucketAndFold(
            history = scenario.toHistory(),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = NOW,
            zoneId = zone,
        )

        assertTrue(fold.points.isEmpty())
        assertNull(fold.footer)
    }

    @Test
    fun `volume metric keeps the shared eligibility even though it ranks differently`() {
        // The 200kg zero-rep set has volume 0 and heaviest-weight 200; under either metric it
        // is ineligible, and the day belongs to the set that was actually performed.
        val scenario = PrRuleFixture.ZERO_REP_SET_IS_NOT_A_RECORD

        val fold = bucketAndFold(
            history = scenario.toHistory(),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.VOLUME_PER_SET,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = NOW,
            zoneId = zone,
        )

        val point = fold.points.single()
        assertEquals(100.0, point.weight)
        assertEquals(5, point.reps)
    }

    @Test
    fun `session metric keeps the shared eligibility in its sum`() {
        // Same claim as the per-set volume test, one fold later: the session total is a SUM
        // over the shared eligibility floor, so the 200kg zero-rep set contributes nothing
        // and the session's total is exactly the performed set's volume. Eligibility only —
        // like VOLUME_PER_SET, the session metric makes no winner-parity claim.
        val scenario = PrRuleFixture.ZERO_REP_SET_IS_NOT_A_RECORD

        val fold = bucketAndFold(
            history = scenario.toHistory(),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = NOW,
            zoneId = zone,
        )

        assertEquals(500.0, fold.points.single().value)
    }

    /**
     * Candidates sharing a `finishedAt` share a session, matching how `PrRuleDbSeeder` lays
     * them out in SQL, and are listed in position order — the order
     * `SessionDao.getHistoryByExercise` delivers them in.
     */
    private fun PrRuleFixture.PrScenario.toHistory(): List<HistoryEntryDomain> = candidates
        .groupBy { it.finishedAt }
        .map { (finishedAt, group) ->
            HistoryEntryDomain(
                sessionUuid = sessionUuidFor(finishedAt),
                finishedAt = finishedAt,
                sets = group.map { HistorySetDomain(weight = it.weight, reps = it.reps) },
            )
        }

    private fun sessionUuidFor(finishedAt: Long): String = "session-$finishedAt"

    private companion object {
        /** Comfortably after every fixture timestamp, all of which sit on the epoch day. */
        const val NOW = 100_000L
    }
}
