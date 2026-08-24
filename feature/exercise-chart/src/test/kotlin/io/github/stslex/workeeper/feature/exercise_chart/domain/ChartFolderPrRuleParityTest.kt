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
 * `ChartFolder` applies the PR eligibility and ordering when it picks each session's set.
 * Parity is claimed under HEAVIEST_WEIGHT only; volume metrics share eligibility, not reps.
 */
internal class ChartFolderPrRuleParityTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `the record-holding set remains represented by its session point`() {
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
            val point = fold.points.single {
                it.sessionUuid == sessionUuidFor(holder.finishedAt)
            }
            assertEquals(sessionUuidFor(holder.finishedAt), point.sessionUuid, "$where — session")
            assertEquals(holder.weight, point.weight, "$where — weight")
            assertEquals(holder.reps, point.reps, "$where — reps")
        }
    }

    @Test
    fun `an ineligible set never becomes the session point`() {
        // A weight-null set on a WEIGHTED exercise is excluded, never coerced to 0.0.
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
        // The 200kg zero-rep set is ineligible under either metric.
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
        // Eligibility only, one fold later: the session total sums over the same floor, so the
        // zero-rep set contributes nothing. No winner-parity claim.
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
     * Candidates sharing a `finishedAt` share a session, and are listed in position order —
     * the order `SessionDao.getHistoryByExercise` delivers.
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
