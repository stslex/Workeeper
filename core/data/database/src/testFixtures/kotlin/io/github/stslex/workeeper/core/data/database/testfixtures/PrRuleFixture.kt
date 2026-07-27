// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.testfixtures

import org.jetbrains.annotations.TestOnly

/**
 * The one description of "which set holds the record", as data.
 *
 * Five places decide this — `SessionDao.getPersonalRecord`, `SessionDao.observePersonalRecord`,
 * `SessionDao.observePersonalRecordsBatch` (via `PersonalRecordRepositoryImpl`),
 * `PrComparator`, and `ChartFolder`'s day-winner. They are in three different modules and
 * cannot share an implementation. They can share a fixture, and that is what this is: every
 * site is fed [SCENARIOS] and must name [PrScenario.expectedHolder].
 *
 * Deliberately plain data — no Room, no Android — so a module that has no database on its
 * test classpath can still be held to the same answers.
 *
 * The fixture that matters most is [WEIGHTLESS_WITH_RESIDUAL_WEIGHTS]. Every pre-existing
 * weightless test seeds `set_table.weight = null`, which is precisely the input on which the
 * batch query and the single-exercise query happened to agree — so the disagreement survived
 * for as long as it did. Residual weights on weightless rows exist in the wild (`set_table`
 * carries no type-conditional constraint) and there is no migration scrubbing them.
 */
@TestOnly
object PrRuleFixture {

    /**
     * One candidate set. [finishedAt] doubles as the session key: candidates sharing a value
     * belong to the same session, which is how [position] becomes reachable as a tiebreak.
     */
    data class PrCandidate(
        val label: String,
        val weight: Double?,
        val reps: Int,
        val position: Int,
        val finishedAt: Long,
    )

    /**
     * [candidates] are listed in canonical comparison order — `finishedAt` ascending, then
     * `position` ascending. Sites that cannot see a timestamp (`PrComparator`) resolve ties
     * by list order, so the list order has to be the order the rule would impose.
     */
    data class PrScenario(
        val name: String,
        val isWeightless: Boolean,
        val candidates: List<PrCandidate>,
        /** Label of the set that must hold the record, or null when nothing is eligible. */
        val expectedHolder: String?,
        val why: String,
    )

    val WEIGHTLESS_WITH_RESIDUAL_WEIGHTS = PrScenario(
        name = "weightless exercise carrying residual set weights",
        isWeightless = true,
        candidates = listOf(
            PrCandidate("residual-weight-8-reps", weight = 50.0, reps = 8, position = 0, finishedAt = 3_000L),
            PrCandidate("no-weight-12-reps", weight = null, reps = 12, position = 0, finishedAt = 4_000L),
        ),
        expectedHolder = "no-weight-12-reps",
        why = "A weightless exercise is ranked on reps alone. A stray weight on the row is " +
            "not a reason to promote it above a set with more reps.",
    )

    val WEIGHT_TIE_BROKEN_BY_REPS = PrScenario(
        name = "equal weight, more reps wins",
        isWeightless = false,
        candidates = listOf(
            PrCandidate("100kg-5-reps", weight = 100.0, reps = 5, position = 0, finishedAt = 1_000L),
            PrCandidate("null-weight-20-reps", weight = null, reps = 20, position = 1, finishedAt = 1_000L),
            PrCandidate("100kg-6-reps", weight = 100.0, reps = 6, position = 0, finishedAt = 2_000L),
        ),
        expectedHolder = "100kg-6-reps",
        why = "Weight ties, so reps decide. The weight-null row is ineligible for a weighted " +
            "exercise however many reps it logged.",
    )

    val TIE_BROKEN_BY_EARLIEST_FINISH = PrScenario(
        name = "equal weight and reps, earliest session keeps it",
        isWeightless = false,
        candidates = listOf(
            PrCandidate("first-session", weight = 80.0, reps = 5, position = 0, finishedAt = 5_000L),
            PrCandidate("second-session", weight = 80.0, reps = 5, position = 0, finishedAt = 6_000L),
        ),
        expectedHolder = "first-session",
        why = "The badge belongs to the first occurrence — repeating a record does not move it.",
    )

    val TIE_BROKEN_BY_POSITION = PrScenario(
        name = "identical sets in one session, lowest position keeps it",
        isWeightless = false,
        candidates = listOf(
            PrCandidate("position-0", weight = 90.0, reps = 5, position = 0, finishedAt = 7_000L),
            PrCandidate("position-1", weight = 90.0, reps = 5, position = 1, finishedAt = 7_000L),
        ),
        expectedHolder = "position-0",
        why = "Same session, so finished_at cannot separate them; position is the last criterion.",
    )

    val ZERO_REP_SET_IS_NOT_A_RECORD = PrScenario(
        name = "a zero-rep set holds nothing however heavy",
        isWeightless = false,
        candidates = listOf(
            PrCandidate("200kg-0-reps", weight = 200.0, reps = 0, position = 0, finishedAt = 8_000L),
            PrCandidate("100kg-5-reps", weight = 100.0, reps = 5, position = 1, finishedAt = 8_000L),
        ),
        expectedHolder = "100kg-5-reps",
        why = "A loaded bar that was never lifted is not a record. reps > 0 is eligibility, " +
            "not a tiebreak.",
    )

    val WEIGHTED_WITH_NO_WEIGHTS_HAS_NO_RECORD = PrScenario(
        name = "weighted exercise whose sets all lack a weight",
        isWeightless = false,
        candidates = listOf(
            PrCandidate("null-weight-10-reps", weight = null, reps = 10, position = 0, finishedAt = 9_000L),
        ),
        expectedHolder = null,
        why = "Nothing is eligible, so there is no holder — not a holder with a zero weight.",
    )

    val WEIGHTLESS_WITH_NO_REPS_HAS_NO_RECORD = PrScenario(
        name = "weightless exercise whose only set logged zero reps",
        isWeightless = true,
        candidates = listOf(
            PrCandidate("zero-reps", weight = null, reps = 0, position = 0, finishedAt = 10_000L),
        ),
        expectedHolder = null,
        why = "Same eligibility floor applies without a weight to fall back on.",
    )

    val SCENARIOS: List<PrScenario> = listOf(
        WEIGHTLESS_WITH_RESIDUAL_WEIGHTS,
        WEIGHT_TIE_BROKEN_BY_REPS,
        TIE_BROKEN_BY_EARLIEST_FINISH,
        TIE_BROKEN_BY_POSITION,
        ZERO_REP_SET_IS_NOT_A_RECORD,
        WEIGHTED_WITH_NO_WEIGHTS_HAS_NO_RECORD,
        WEIGHTLESS_WITH_NO_REPS_HAS_NO_RECORD,
    )
}
